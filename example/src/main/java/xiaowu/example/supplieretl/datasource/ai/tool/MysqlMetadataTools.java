package xiaowu.example.supplieretl.datasource.ai.tool;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaowu.example.supplieretl.datasource.application.service.DataSourceConnectionApplicationService;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceConnection;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlDataSourceConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class MysqlMetadataTools {

  private static final int SAMPLE_ROW_LIMIT = 2;

  private final DataSourceConnectionApplicationService connectionService;
  private final ObjectMapper objectMapper;

  @Tool(description = """
      Inspect the saved MySQL connection in the current tool context and return the whole database catalog.
      Always use this tool before generating ingestion rules.
      The response includes every table, every column, and up to two sample rows per table.
      If a table cannot be sampled, the response still keeps its field list and records the sampling error.
      """)
  public String inspectSavedMysqlCatalog(ToolContext toolContext) {
    Long connectionId = extractConnectionId(toolContext);
    if (connectionId == null) {
      return toError("CONNECTION_ID_MISSING", "Tool context does not contain connectionId");
    }

    DataSourceConnection connection = requireMysqlConnection(connectionId);
    MysqlDataSourceConfig mysqlConfig = (MysqlDataSourceConfig) connection.getConfig();

    try {
      Class.forName(mysqlConfig.driverClassName());
    } catch (ClassNotFoundException ex) {
      return toError("MYSQL_DRIVER_MISSING", "MySQL driver not found: " + mysqlConfig.driverClassName());
    }

    try (Connection jdbcConnection = DriverManager.getConnection(
        mysqlConfig.jdbcUrl(),
        mysqlConfig.username(),
        mysqlConfig.password() == null ? "" : mysqlConfig.password())) {
      DatabaseMetaData metaData = jdbcConnection.getMetaData();
      String catalog = normalizeText(jdbcConnection.getCatalog());
      List<Map<String, Object>> tables = loadTables(metaData, catalog, jdbcConnection);

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("success", true);
      payload.put("connectionId", connectionId);
      payload.put("connectionName", connection.getConnectionName());
      payload.put("database", catalog);
      payload.put("tableCount", tables.size());
      payload.put("tables", tables);
      return toJson(payload);
    } catch (Exception ex) {
      log.warn("Inspecting MySQL catalog failed, connectionId={}", connectionId, ex);
      return toError("MYSQL_INSPECTION_FAILED", ex.getMessage());
    }
  }

  @Tool(description = """
      Inspect a specific table from the saved MySQL connection in the current tool context.
      Use this tool when you need focused detail for a table after reviewing the whole catalog.
      The response includes columns and up to two sample rows.
      """)
  public String inspectSavedMysqlTable(
      @ToolParam(description = "Exact table name from the saved MySQL database", required = true) String tableName,
      ToolContext toolContext) {
    Long connectionId = extractConnectionId(toolContext);
    if (connectionId == null) {
      return toError("CONNECTION_ID_MISSING", "Tool context does not contain connectionId");
    }
    if (tableName == null || tableName.isBlank()) {
      return toError("TABLE_NAME_MISSING", "tableName must not be blank");
    }

    DataSourceConnection connection = requireMysqlConnection(connectionId);
    MysqlDataSourceConfig mysqlConfig = (MysqlDataSourceConfig) connection.getConfig();

    try {
      Class.forName(mysqlConfig.driverClassName());
    } catch (ClassNotFoundException ex) {
      return toError("MYSQL_DRIVER_MISSING", "MySQL driver not found: " + mysqlConfig.driverClassName());
    }

    try (Connection jdbcConnection = DriverManager.getConnection(
        mysqlConfig.jdbcUrl(),
        mysqlConfig.username(),
        mysqlConfig.password() == null ? "" : mysqlConfig.password())) {
      DatabaseMetaData metaData = jdbcConnection.getMetaData();
      String catalog = normalizeText(jdbcConnection.getCatalog());
      Map<String, Object> table = loadSingleTable(metaData, catalog, jdbcConnection, tableName.trim());
      if (table == null) {
        return toError("TABLE_NOT_FOUND", "Table not found: " + tableName);
      }

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("success", true);
      payload.put("connectionId", connectionId);
      payload.put("connectionName", connection.getConnectionName());
      payload.put("database", catalog);
      payload.put("table", table);
      return toJson(payload);
    } catch (Exception ex) {
      log.warn("Inspecting MySQL table failed, connectionId={}, table={}", connectionId, tableName, ex);
      return toError("MYSQL_TABLE_INSPECTION_FAILED", ex.getMessage());
    }
  }

  private List<Map<String, Object>> loadTables(
      DatabaseMetaData metaData,
      String catalog,
      Connection jdbcConnection) throws Exception {
    List<Map<String, Object>> tables = new ArrayList<>();
    try (ResultSet resultSet = metaData.getTables(catalog, null, "%", new String[] { "TABLE" })) {
      while (resultSet.next()) {
        String tableName = resultSet.getString("TABLE_NAME");
        Map<String, Object> table = loadSingleTable(metaData, catalog, jdbcConnection, tableName);
        if (table != null) {
          tables.add(table);
        }
      }
    }
    return tables;
  }

  private Map<String, Object> loadSingleTable(
      DatabaseMetaData metaData,
      String catalog,
      Connection jdbcConnection,
      String tableName) throws Exception {
    List<Map<String, Object>> columns = loadColumns(metaData, catalog, tableName);
    if (columns.isEmpty()) {
      return null;
    }

    Map<String, Object> table = new LinkedHashMap<>();
    table.put("tableName", tableName);
    table.put("columns", columns);

    try {
      table.put("sampleRows", loadSampleRows(jdbcConnection, tableName));
    } catch (Exception ex) {
      table.put("sampleRows", List.of());
      table.put("sampleRowsError", ex.getMessage());
    }
    return table;
  }

  private List<Map<String, Object>> loadColumns(
      DatabaseMetaData metaData,
      String catalog,
      String tableName) throws Exception {
    List<Map<String, Object>> columns = new ArrayList<>();
    try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, "%")) {
      while (resultSet.next()) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("name", resultSet.getString("COLUMN_NAME"));
        column.put("type", resultSet.getString("TYPE_NAME"));
        column.put("size", resultSet.getInt("COLUMN_SIZE"));
        column.put("nullable", resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
        column.put("defaultValue", normalizeText(resultSet.getString("COLUMN_DEF")));
        column.put("remarks", normalizeText(resultSet.getString("REMARKS")));
        columns.add(column);
      }
    }
    return columns;
  }

  private List<Map<String, Object>> loadSampleRows(Connection jdbcConnection, String tableName) throws Exception {
    List<Map<String, Object>> rows = new ArrayList<>();
    String sql = "SELECT * FROM " + quoteIdentifier(tableName) + " LIMIT " + SAMPLE_ROW_LIMIT;
    try (Statement statement = jdbcConnection.createStatement()) {
      statement.setQueryTimeout(10);
      try (ResultSet resultSet = statement.executeQuery(sql)) {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (resultSet.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int index = 1; index <= columnCount; index++) {
            row.put(metaData.getColumnLabel(index), resultSet.getObject(index));
          }
          rows.add(row);
        }
      }
    }
    return rows;
  }

  private DataSourceConnection requireMysqlConnection(Long connectionId) {
    DataSourceConnection connection = connectionService.getConnection(connectionId);
    if (connection.getType() != DataSourceType.MYSQL) {
      throw new IllegalArgumentException("AI assistant only supports saved MySQL connections");
    }
    if (!(connection.getConfig() instanceof MysqlDataSourceConfig)) {
      throw new IllegalArgumentException("Connection config is not MySQL");
    }
    return connection;
  }

  private Long extractConnectionId(ToolContext toolContext) {
    if (toolContext == null || toolContext.getContext() == null) {
      return null;
    }
    Object raw = toolContext.getContext().get("connectionId");
    if (raw instanceof Long value) {
      return value;
    }
    if (raw instanceof Number value) {
      return value.longValue();
    }
    if (raw instanceof String value) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return null;
  }

  private String quoteIdentifier(String identifier) {
    return "`" + Objects.requireNonNull(identifier).replace("`", "``") + "`";
  }

  private String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String toError(String code, String message) {
    return toJson(Map.of(
        "success", false,
        "errorCode", code,
        "errorMessage", message));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      log.error("Serializing MySQL metadata tool response failed", ex);
      return "{\"success\":false,\"errorCode\":\"JSON_SERIALIZATION_FAILED\",\"errorMessage\":\"Failed to serialize tool response\"}";
    }
  }
}
