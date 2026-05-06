package xiaowu.example.supplieretl.datasource.domain.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import xiaowu.example.supplieretl.datasource.domain.transform.MysqlTransformRuleRegistry;

/**
 * MySQL 数据导入请求。
 *
 * 支持两种模式：
 * 1. 直接提供自定义 SELECT 语句。
 * 2. 通过 sourceTable + fieldMappings + filters + transformRules + target
 * 进行可视化规则构建。
 */
public record MysqlIngestionRequest(
    String query,
    String sourceTable,
    List<FieldMappingRule> fieldMappings,
    List<FilterRule> filters,
    List<TransformRule> transformRules,
    TargetConfig target,
    String sortField,
    String sortDirection,
    Integer maxRows) implements IngestionRequest {

  private static final int DEFAULT_MAX_ROWS = 1000;
  private static final int MAX_ROWS_LIMIT = 50_000;
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");
  private static final Pattern TARGET_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
  private static final Set<String> FILTER_OPERATORS = Set.of(
      "EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "IN", "IS_NULL", "NOT_NULL");
  private static final Set<String> FILTER_LOGICS = Set.of("AND", "OR");
  private static final Set<String> TARGET_WRITE_MODES = Set.of("APPEND", "UPSERT", "REPLACE");
  private static final Set<String> SORT_DIRECTIONS = Set.of("ASC", "DESC");
  private static final MysqlTransformRuleRegistry TRANSFORM_RULE_REGISTRY = MysqlTransformRuleRegistry
      .defaultRegistry();

  public MysqlIngestionRequest {
    query = DataSourceConfigSupport.normalizeNullableText(query);
    sourceTable = DataSourceConfigSupport.normalizeNullableText(sourceTable);
    fieldMappings = fieldMappings == null ? List.of()
        : fieldMappings.stream()
            .filter(Objects::nonNull)
            .toList();
    filters = filters == null ? List.of()
        : filters.stream()
            .filter(Objects::nonNull)
            .toList();
    transformRules = transformRules == null ? List.of()
        : transformRules.stream()
            .filter(Objects::nonNull)
            .toList();
    target = target == null ? TargetConfig.empty() : target;
    sortField = DataSourceConfigSupport.normalizeNullableText(sortField);
    sortDirection = DataSourceConfigSupport.defaultIfBlank(sortDirection, "DESC").toUpperCase(Locale.ROOT);
    maxRows = maxRows == null ? DEFAULT_MAX_ROWS : maxRows;
  }

  public MysqlIngestionRequest(String query, Integer maxRows) {
    this(query, null, List.of(), List.of(), List.of(), TargetConfig.empty(), null, null, maxRows);
  }

  public MysqlIngestionRequest(String query) {
    this(query, null);
  }

  @Override
  public void validate() {
    if (maxRows <= 0 || maxRows > MAX_ROWS_LIMIT) {
      throw new IllegalArgumentException("maxRows must be between 1 and " + MAX_ROWS_LIMIT);
    }

    if (useCustomQuery()) {
      assertSelectOnly(query);
    } else {
      DataSourceConfigSupport.requireText(sourceTable, "sourceTable");
      assertSafeIdentifier(sourceTable, "sourceTable");

      List<FieldMappingRule> enabledMappings = enabledFieldMappings();
      if (enabledMappings.isEmpty()) {
        throw new IllegalArgumentException("At least one enabled field mapping is required");
      }

      enabledMappings.forEach(FieldMappingRule::validate);
      long distinctTargetFields = enabledMappings.stream()
          .map(FieldMappingRule::targetField)
          .distinct()
          .count();
      if (distinctTargetFields != enabledMappings.size()) {
        throw new IllegalArgumentException("fieldMappings.targetField must be unique");
      }
      enabledFilters().forEach(FilterRule::validate);
      enabledTransformRules().forEach(TransformRule::validate);

      if (sortField != null) {
        assertSafeIdentifier(sortField, "sortField");
      }
      if (!SORT_DIRECTIONS.contains(sortDirection)) {
        throw new IllegalArgumentException("sortDirection must be ASC or DESC");
      }
      if (target.incrementalField() != null) {
        assertSafeIdentifier(target.incrementalField(), "target.incrementalField");
      }
    }

    if (useCustomQuery()) {
      enabledFieldMappings().forEach(FieldMappingRule::validate);
      enabledFilters().forEach(FilterRule::validate);
      enabledTransformRules().forEach(TransformRule::validate);
    }

    target.validate();
  }

  public boolean useCustomQuery() {
    return query != null;
  }

  public List<FieldMappingRule> enabledFieldMappings() {
    return fieldMappings.stream()
        .filter(FieldMappingRule::isEnabled)
        .toList();
  }

  public List<FilterRule> enabledFilters() {
    return filters.stream()
        .filter(FilterRule::isEnabled)
        .toList();
  }

  public List<TransformRule> enabledTransformRules() {
    return transformRules.stream()
        .filter(TransformRule::isEnabled)
        .toList();
  }

  public String resolveSortField() {
    return sortField != null ? sortField : target.incrementalField();
  }

  private static void assertSelectOnly(String sql) {
    String upper = sql.stripLeading().toUpperCase(Locale.ROOT);
    if (!upper.startsWith("SELECT")) {
      throw new IllegalArgumentException("Only SELECT statements are allowed");
    }
    String[] forbidden = { "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CREATE", "GRANT", "REVOKE" };
    for (String keyword : forbidden) {
      if (upper.contains(keyword)) {
        throw new IllegalArgumentException("SQL contains forbidden keyword: " + keyword);
      }
    }
  }

  private static void assertSafeIdentifier(String identifier, String fieldName) {
    if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
      throw new IllegalArgumentException(fieldName + " contains illegal identifier: " + identifier);
    }
  }

  public record FieldMappingRule(
      String sourceField,
      String targetField,
      Boolean enabled) {

    public FieldMappingRule {
      sourceField = DataSourceConfigSupport.normalizeNullableText(sourceField);
      String defaultTargetField = sourceField == null
          ? null
          : sourceField.contains(".")
              ? sourceField.substring(sourceField.lastIndexOf('.') + 1)
              : sourceField;
      targetField = DataSourceConfigSupport.defaultIfBlank(targetField, defaultTargetField);
      enabled = enabled == null || enabled;
    }

    public boolean isEnabled() {
      return Boolean.TRUE.equals(enabled);
    }

    public void validate() {
      if (!isEnabled()) {
        return;
      }
      String validatedSource = DataSourceConfigSupport.requireText(sourceField, "fieldMappings.sourceField");
      String validatedTarget = DataSourceConfigSupport.requireText(targetField, "fieldMappings.targetField");
      assertSafeIdentifier(validatedSource, "fieldMappings.sourceField");
      assertSafeIdentifier(validatedTarget, "fieldMappings.targetField");
    }
  }

  public record FilterRule(
      String logic,
      String field,
      String operator,
      String value,
      Boolean enabled) {

    public FilterRule {
      logic = DataSourceConfigSupport.defaultIfBlank(logic, "AND").toUpperCase(Locale.ROOT);
      field = DataSourceConfigSupport.normalizeNullableText(field);
      operator = DataSourceConfigSupport.defaultIfBlank(operator, "EQ").toUpperCase(Locale.ROOT);
      value = DataSourceConfigSupport.normalizeNullableText(value);
      enabled = enabled == null || enabled;
    }

    public boolean isEnabled() {
      return Boolean.TRUE.equals(enabled);
    }

    public void validate() {
      if (!isEnabled()) {
        return;
      }
      String validatedField = DataSourceConfigSupport.requireText(field, "filters.field");
      assertSafeIdentifier(validatedField, "filters.field");
      if (!FILTER_LOGICS.contains(logic)) {
        throw new IllegalArgumentException("filters.logic must be AND or OR");
      }
      if (!FILTER_OPERATORS.contains(operator)) {
        throw new IllegalArgumentException("Unsupported filter operator: " + operator);
      }
      if (!"IS_NULL".equals(operator) && !"NOT_NULL".equals(operator) && value == null) {
        throw new IllegalArgumentException("filters.value is required for operator " + operator);
      }
    }
  }

  public record TransformRule(
      String field,
      String targetField,
      String transformType,
      String argument,
      Boolean enabled) {

    public TransformRule {
      field = DataSourceConfigSupport.normalizeNullableText(field);
      targetField = DataSourceConfigSupport.defaultIfBlank(targetField, field);
      transformType = DataSourceConfigSupport.defaultIfBlank(transformType, "TRIM").toUpperCase(Locale.ROOT);
      argument = DataSourceConfigSupport.normalizeNullableText(argument);
      enabled = enabled == null || enabled;
    }

    public boolean isEnabled() {
      return Boolean.TRUE.equals(enabled);
    }

    public void validate() {
      if (!isEnabled()) {
        return;
      }
      String validatedField = DataSourceConfigSupport.requireText(field, "transformRules.field");
      String validatedTargetField = DataSourceConfigSupport.requireText(targetField, "transformRules.targetField");
      assertSafeIdentifier(validatedField, "transformRules.field");
      assertSafeIdentifier(validatedTargetField, "transformRules.targetField");
      TRANSFORM_RULE_REGISTRY.validateRule(transformType, argument);
    }
  }

  public record TargetConfig(
      String targetName,
      String writeMode,
      String primaryKey,
      String incrementalField) {

    public TargetConfig {
      targetName = DataSourceConfigSupport.normalizeNullableText(targetName);
      writeMode = DataSourceConfigSupport.defaultIfBlank(writeMode, "APPEND").toUpperCase(Locale.ROOT);
      primaryKey = DataSourceConfigSupport.normalizeNullableText(primaryKey);
      incrementalField = DataSourceConfigSupport.normalizeNullableText(incrementalField);
    }

    public static TargetConfig empty() {
      return new TargetConfig(null, "APPEND", null, null);
    }

    public void validate() {
      if (targetName != null && !TARGET_NAME_PATTERN.matcher(targetName).matches()) {
        throw new IllegalArgumentException("target.targetName contains illegal characters: " + targetName);
      }
      if (!TARGET_WRITE_MODES.contains(writeMode)) {
        throw new IllegalArgumentException("target.writeMode must be one of " + TARGET_WRITE_MODES);
      }
      if (primaryKey != null) {
        assertSafeIdentifier(primaryKey, "target.primaryKey");
      }
      if (incrementalField != null) {
        assertSafeIdentifier(incrementalField, "target.incrementalField");
      }
    }
  }
}
