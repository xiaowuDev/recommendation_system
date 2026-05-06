package xiaowu.example.supplieretl.datasource.infrastructure.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlDataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.transform.MysqlTransformRuleRegistry;

@Component
public class MysqlDataIngester implements DataIngester {

  private static final int QUERY_TIMEOUT_SECONDS = 30;
  private static final MysqlTransformRuleRegistry TRANSFORM_RULE_REGISTRY = MysqlTransformRuleRegistry.defaultRegistry();

  @Override
  public DataSourceType supports() {
    return DataSourceType.MYSQL;
  }

  @Override
  public IngestionResult ingest(DataSourceConfig config, IngestionRequest request) {
    MysqlDataSourceConfig mysqlConfig = (MysqlDataSourceConfig) config;
    MysqlIngestionRequest mysqlRequest = (MysqlIngestionRequest) request;

    try {
      Class.forName(mysqlConfig.driverClassName());
    } catch (ClassNotFoundException ex) {
      throw new IllegalStateException("MySQL driver not found: " + mysqlConfig.driverClassName(), ex);
    }

    try (Connection conn = DriverManager.getConnection(
        mysqlConfig.jdbcUrl(),
        mysqlConfig.username(),
        mysqlConfig.password() == null ? "" : mysqlConfig.password())) {

      conn.setReadOnly(true);
      return mysqlRequest.useCustomQuery()
          ? executeCustomQuery(conn, mysqlConfig, mysqlRequest)
          : executeRuleQuery(conn, mysqlConfig, mysqlRequest);
    } catch (Exception ex) {
      throw new IllegalStateException("MySQL ingestion failed: " + ex.getMessage(), ex);
    }
  }

  private IngestionResult executeCustomQuery(
      Connection conn,
      MysqlDataSourceConfig mysqlConfig,
      MysqlIngestionRequest request) throws Exception {
    try (Statement stmt = conn.createStatement()) {
      stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      stmt.setMaxRows(request.maxRows());

      long startTime = System.currentTimeMillis();
      try (ResultSet rs = stmt.executeQuery(request.query())) {
        long queryTimeMs = System.currentTimeMillis() - startTime;
        return buildResult(rs, mysqlConfig, request, request.query(), queryTimeMs, true);
      }
    }
  }

  private IngestionResult executeRuleQuery(
      Connection conn,
      MysqlDataSourceConfig mysqlConfig,
      MysqlIngestionRequest request) throws Exception {
    BuiltQuery builtQuery = buildQuery(request);
    try (PreparedStatement stmt = conn.prepareStatement(builtQuery.sql())) {
      stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      stmt.setMaxRows(request.maxRows());
      for (int i = 0; i < builtQuery.parameters().size(); i++) {
        stmt.setObject(i + 1, builtQuery.parameters().get(i));
      }

      long startTime = System.currentTimeMillis();
      try (ResultSet rs = stmt.executeQuery()) {
        long queryTimeMs = System.currentTimeMillis() - startTime;
        return buildResult(rs, mysqlConfig, request, builtQuery.sql(), queryTimeMs, false);
      }
    }
  }

  private IngestionResult buildResult(
      ResultSet rs,
      MysqlDataSourceConfig mysqlConfig,
      MysqlIngestionRequest request,
      String resolvedQuery,
      long queryTimeMs,
      boolean customMode) throws Exception {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();
    List<String> sourceColumns = new ArrayList<>(columnCount);
    for (int i = 1; i <= columnCount; i++) {
      sourceColumns.add(meta.getColumnLabel(i));
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    while (rs.next() && rows.size() < request.maxRows()) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        row.put(sourceColumns.get(i - 1), rs.getObject(i));
      }
      rows.add(row);
    }

    List<Map<String, Object>> transformedRows = customMode && !request.enabledFieldMappings().isEmpty()
        ? applyFieldMappings(rows, request.enabledFieldMappings())
        : rows;

    transformedRows = applyTransformRules(
        transformedRows,
        request.enabledTransformRules(),
        request.enabledFieldMappings());
    transformedRows = applyTargetConfig(transformedRows, request.target());

    List<String> finalColumns = deriveColumns(transformedRows, sourceColumns);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("jdbcUrl", mysqlConfig.jdbcUrl());
    metadata.put("resolvedQuery", resolvedQuery);
    metadata.put("queryTimeMs", queryTimeMs);
    metadata.put("sourceColumnCount", columnCount);
    metadata.put("resultColumnCount", finalColumns.size());
    metadata.put("mode", customMode ? "CUSTOM_SQL" : "RULE_BUILDER");
    metadata.put("fieldMappings", toFieldMappingMetadata(request.enabledFieldMappings()));
    metadata.put("filters", toFilterMetadata(request.enabledFilters()));
    metadata.put("transformRules", toTransformMetadata(request.enabledTransformRules()));
    metadata.put("target", toTargetMetadata(request.target()));

    return IngestionResult.of(
        transformedRows.size(),
        finalColumns,
        transformedRows,
        Collections.unmodifiableMap(metadata));
  }

  private BuiltQuery buildQuery(MysqlIngestionRequest request) {
    StringJoiner selectClause = new StringJoiner(", ");
    for (MysqlIngestionRequest.FieldMappingRule mapping : request.enabledFieldMappings()) {
      selectClause.add(mapping.sourceField() + " AS " + mapping.targetField());
    }

    StringBuilder sql = new StringBuilder("SELECT ")
        .append(selectClause)
        .append(" FROM ")
        .append(request.sourceTable());

    List<Object> parameters = new ArrayList<>();
    List<MysqlIngestionRequest.FilterRule> filters = request.enabledFilters();
    if (!filters.isEmpty()) {
      sql.append(" WHERE ");
      for (int i = 0; i < filters.size(); i++) {
        MysqlIngestionRequest.FilterRule filter = filters.get(i);
        if (i > 0) {
          sql.append(' ').append(filter.logic()).append(' ');
        }
        appendFilter(sql, parameters, filter);
      }
    }

    String sortField = request.resolveSortField();
    if (sortField != null) {
      sql.append(" ORDER BY ").append(sortField).append(' ').append(request.sortDirection());
    }

    return new BuiltQuery(sql.toString(), List.copyOf(parameters));
  }

  private static void appendFilter(
      StringBuilder sql,
      List<Object> parameters,
      MysqlIngestionRequest.FilterRule filter) {
    sql.append(filter.field()).append(' ');
    switch (filter.operator()) {
      case "EQ" -> {
        sql.append("= ?");
        parameters.add(filter.value());
      }
      case "NE" -> {
        sql.append("<> ?");
        parameters.add(filter.value());
      }
      case "GT" -> {
        sql.append("> ?");
        parameters.add(filter.value());
      }
      case "GTE" -> {
        sql.append(">= ?");
        parameters.add(filter.value());
      }
      case "LT" -> {
        sql.append("< ?");
        parameters.add(filter.value());
      }
      case "LTE" -> {
        sql.append("<= ?");
        parameters.add(filter.value());
      }
      case "LIKE" -> {
        sql.append("LIKE ?");
        parameters.add(filter.value());
      }
      case "IN" -> {
        String[] values = filter.value().split(",");
        sql.append("IN (");
        StringJoiner placeholders = new StringJoiner(", ");
        for (String value : values) {
          placeholders.add("?");
          parameters.add(value.trim());
        }
        sql.append(placeholders).append(')');
      }
      case "IS_NULL" -> sql.append("IS NULL");
      case "NOT_NULL" -> sql.append("IS NOT NULL");
      default -> throw new IllegalArgumentException("Unsupported filter operator: " + filter.operator());
    }
  }

  private static List<Map<String, Object>> applyFieldMappings(
      List<Map<String, Object>> rows,
      List<MysqlIngestionRequest.FieldMappingRule> mappings) {
    List<Map<String, Object>> mappedRows = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      Map<String, Object> mapped = new LinkedHashMap<>();
      for (MysqlIngestionRequest.FieldMappingRule mapping : mappings) {
        mapped.put(mapping.targetField(), row.get(mapping.sourceField()));
      }
      mappedRows.add(mapped);
    }
    return mappedRows;
  }

  private static List<Map<String, Object>> applyTransformRules(
      List<Map<String, Object>> rows,
      List<MysqlIngestionRequest.TransformRule> rules,
      List<MysqlIngestionRequest.FieldMappingRule> mappings) {
    if (rules.isEmpty()) {
      return rows;
    }

    Map<String, String> sourceToTargetField = new LinkedHashMap<>();
    for (MysqlIngestionRequest.FieldMappingRule mapping : mappings) {
      sourceToTargetField.put(mapping.sourceField(), mapping.targetField());
    }

    List<Map<String, Object>> transformedRows = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      Map<String, Object> transformed = new LinkedHashMap<>(row);
      for (MysqlIngestionRequest.TransformRule rule : rules) {
        String resolvedInputField = resolveTransformInputField(rule.field(), transformed, sourceToTargetField);
        Object rawValue = transformed.get(resolvedInputField);
        Object nextValue = transformValue(rawValue, rule.transformType(), rule.argument());
        transformed.put(rule.targetField(), nextValue);
      }
      transformedRows.add(transformed);
    }
    return transformedRows;
  }

  private static String resolveTransformInputField(
      String requestedField,
      Map<String, Object> row,
      Map<String, String> sourceToTargetField) {
    if (row.containsKey(requestedField)) {
      return requestedField;
    }

    String mappedTargetField = sourceToTargetField.get(requestedField);
    if (mappedTargetField != null && row.containsKey(mappedTargetField)) {
      return mappedTargetField;
    }

    return requestedField;
  }

  private static Object transformValue(Object rawValue, String transformType, String argument) {
    return TRANSFORM_RULE_REGISTRY.apply(transformType, rawValue, argument);
  }

  private static List<Map<String, Object>> applyTargetConfig(
      List<Map<String, Object>> rows,
      MysqlIngestionRequest.TargetConfig target) {
    if (!"UPSERT".equals(target.writeMode()) || target.primaryKey() == null) {
      return rows;
    }

    LinkedHashMap<Object, Map<String, Object>> deduplicated = new LinkedHashMap<>();
    List<Map<String, Object>> rowsWithoutKey = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Object key = row.get(target.primaryKey());
      if (key == null) {
        rowsWithoutKey.add(row);
        continue;
      }
      deduplicated.remove(key);
      deduplicated.put(key, row);
    }

    List<Map<String, Object>> merged = new ArrayList<>(deduplicated.values());
    merged.addAll(rowsWithoutKey);
    return merged;
  }

  private static List<String> deriveColumns(List<Map<String, Object>> rows, List<String> fallbackColumns) {
    if (rows.isEmpty()) {
      return fallbackColumns;
    }
    LinkedHashSet<String> columns = new LinkedHashSet<>();
    rows.forEach(row -> columns.addAll(row.keySet()));
    return List.copyOf(columns);
  }

  private static List<Map<String, Object>> toFieldMappingMetadata(
      List<MysqlIngestionRequest.FieldMappingRule> mappings) {
    return mappings.stream()
        .map(mapping -> {
          Map<String, Object> values = new LinkedHashMap<>();
          values.put("sourceField", mapping.sourceField());
          values.put("targetField", mapping.targetField());
          return values;
        })
        .toList();
  }

  private static List<Map<String, Object>> toFilterMetadata(
      List<MysqlIngestionRequest.FilterRule> filters) {
    return filters.stream()
        .map(filter -> {
          Map<String, Object> values = new LinkedHashMap<>();
          values.put("logic", filter.logic());
          values.put("field", filter.field());
          values.put("operator", filter.operator());
          values.put("value", filter.value());
          return values;
        })
        .toList();
  }

  private static List<Map<String, Object>> toTransformMetadata(
      List<MysqlIngestionRequest.TransformRule> rules) {
    return rules.stream()
        .map(rule -> {
          Map<String, Object> values = new LinkedHashMap<>();
          values.put("field", rule.field());
          values.put("targetField", rule.targetField());
          values.put("transformType", rule.transformType());
          values.put("argument", rule.argument());
          return values;
        })
        .toList();
  }

  private static Map<String, Object> toTargetMetadata(MysqlIngestionRequest.TargetConfig target) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("targetName", target.targetName());
    values.put("writeMode", target.writeMode());
    values.put("primaryKey", target.primaryKey());
    values.put("incrementalField", target.incrementalField());
    return values;
  }

  private record BuiltQuery(
      String sql,
      List<Object> parameters) {
  }
}
