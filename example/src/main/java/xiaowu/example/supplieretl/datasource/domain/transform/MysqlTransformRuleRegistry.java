package xiaowu.example.supplieretl.datasource.domain.transform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * MySQL数据转换规则注册中心
 */
public final class MysqlTransformRuleRegistry {

  private static final MysqlTransformRuleRegistry DEFAULT = new MysqlTransformRuleRegistry(
      MysqlBuiltInTransformRules.definitions());

  private final Map<String, MysqlTransformRuleDefinition> definitions;

  private MysqlTransformRuleRegistry(List<MysqlTransformRuleDefinition> definitions) {
    /**
     * 保留规则注册顺序
     */
    Map<String, MysqlTransformRuleDefinition> values = new LinkedHashMap<>();
    for (MysqlTransformRuleDefinition definition : definitions) {
      MysqlTransformRuleDefinition previous = values.put(definition.code(), definition);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicate transform rule code: " + definition.code());
      }
    }
    this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static MysqlTransformRuleRegistry defaultRegistry() {
    return DEFAULT;
  }

  /**
   * 获取所有支持的MySQL转换规则能力列表，每个能力包含转换规则的代码、显示名称、描述、参数模式和参数提示信息。
   *
   * @return MySQL转换规则能力列表，用于展示和使用MySQL数据转换功能
   */
  public List<MysqlTransformRuleCapability> capabilities() {
    /**
     * 更直白的写法
     * List<MysqlTransformRuleCapability> result = new ArrayList<>();
     * for (MysqlTransformRuleDefinition definition : definitions.values()) {
     * result.add(new MysqlTransformRuleCapability(
     * definition.code(),
     * definition.displayName(),
     * definition.description(),
     * definition.argumentMode().name(),
     * definition.argumentHint()));
     * }
     * return result;
     */
    return definitions.values().stream()
        .map(definition -> new MysqlTransformRuleCapability(
            definition.code(),
            definition.displayName(),
            definition.description(),
            definition.argumentMode().name(),
            definition.argumentHint()))
        .toList();
  }

  /**
   * 获取所有支持的MySQL转换规则代码列表，用于验证用户输入的转换规则代码是否合法。
   *
   * @return MySQL转换规则代码列表，用于验证用户输入的转换规则代码是否合法
   */
  public List<String> supportedRuleCodes() {
    return definitions.keySet().stream().toList();
  }

  public String promptSummary() {
    StringJoiner joiner = new StringJoiner("\n");
    for (MysqlTransformRuleCapability capability : capabilities()) {
      String line = " - %s (%s): %s".formatted(
          capability.code(),
          capability.argumentMode(),
          capability.description());
      joiner.add(line);
    }
    return joiner.toString();
  }

  public void validateRule(String transformType, String argument) {
    MysqlTransformRuleDefinition definition = requireDefinition(transformType);
    definition.validateArgument(argument);
  }

  public Object apply(String transformType, Object rawValue, String argument) {
    MysqlTransformRuleDefinition definition = requireDefinition(transformType);
    definition.validateArgument(argument);
    return definition.executor().apply(rawValue, argument);
  }

  private MysqlTransformRuleDefinition requireDefinition(String transformType) {
    String normalizedType = normalizeCode(transformType);
    MysqlTransformRuleDefinition definition = definitions.get(normalizedType);
    if (definition == null) {
      throw new IllegalArgumentException("Unsupported transform type: " + transformType);
    }
    return definition;
  }

  private static String normalizeCode(String transformType) {
    if (transformType == null || transformType.isBlank()) {
      return "";
    }
    return transformType.trim().toUpperCase(Locale.ROOT);
  }

  /**
   * 构建mysql数据转换规则能力
   *
   * @param code         转换规则代码，唯一标识一种转换类型，如UPPERCASE、DIVIDE等
   * @param displayName  转换规则显示名称，用于用户界面展示
   * @param description  转换规则描述，详细说明转换规则的功能和使用场
   * @param argumentMode 转换规则参数模式，指示转换规则是否需要参数以及参数的可选性（如NONE、OPTIONAL、REQUIRED）
   * @param argumentHint 转换规则参数提示，当argumentMode为OPTIONAL或REQUIRED时，提供参数的使用提示信息
   */
  public record MysqlTransformRuleCapability(
      String code,
      String displayName,
      String description,
      String argumentMode,
      String argumentHint) {
  }

  /**
   * ArgumentMode枚举定义了MySQL转换规则的参数模式，指示转换规则是否需要参数以及参数的可选性。
   */
  private enum ArgumentMode {
    NONE,
    OPTIONAL,
    REQUIRED
  }

  @FunctionalInterface
  interface TransformExecutor {
    Object apply(Object rawValue, String argument);
  }

  /**
   * FunctionalInterface注解: 定义一个接口为函数式接口,确保接口有且仅有一个抽象方法,以便于使用Lambda表达式或方法引用来实现该接口
   */
  @FunctionalInterface
  interface ArgumentValidator {
    void validate(String argument);
  }

  /**
   * Mysql转换规则定义，包含转换规则的元数据和执行逻辑
   *
   * @param code              转换规则代码，唯一标识一种转换类型，如UPPERCASE、DIVIDE等
   * @param displayName       转换规则显示名称，用于用户界面展示
   * @param description       转换规则描述，详细说明转换规则的功能和使用场景
   * @param argumentMode      转换规则参数模式，指示转换规则是否需要参数以及参数的可选性（如NONE、OPTIONAL、REQUIRED）
   * @param argumentHint      转换规则参数提示，当argumentMode为OPTIONAL或REQUIRED时，提供参数的使用提示信息
   * @param argumentValidator 转换规则参数验证器，用于验证传入参数的合法性
   * @param executor          转换规则执行器，包含实际的转换逻辑，根据原始值和参数计算转换结果
   */
  record MysqlTransformRuleDefinition(
      String code,
      String displayName,
      String description,
      ArgumentMode argumentMode,
      String argumentHint,
      ArgumentValidator argumentValidator,
      TransformExecutor executor) {

    MysqlTransformRuleDefinition {
      code = Objects.requireNonNull(code, "code must not be null").trim().toUpperCase(Locale.ROOT);
      displayName = Objects.requireNonNull(displayName, "displayName must not be null").trim();
      description = Objects.requireNonNull(description, "description must not be null").trim();
      argumentMode = Objects.requireNonNull(argumentMode, "argumentMode must not be null");
      argumentValidator = Objects.requireNonNull(argumentValidator, "argumentValidator must not be null");
      executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    static MysqlTransformRuleDefinition none(
        String code,
        String displayName,
        String description,
        TransformExecutor executor) {
      return new MysqlTransformRuleDefinition(
          code,
          displayName,
          description,
          ArgumentMode.NONE,
          null,
          argument -> {
          },
          executor);
    }

    static MysqlTransformRuleDefinition optional(
        String code,
        String displayName,
        String description,
        String argumentHint,
        ArgumentValidator argumentValidator,
        TransformExecutor executor) {
      return new MysqlTransformRuleDefinition(
          code,
          displayName,
          description,
          ArgumentMode.OPTIONAL,
          argumentHint,
          argumentValidator,
          executor);
    }

    static MysqlTransformRuleDefinition required(
        String code,
        String displayName,
        String description,
        String argumentHint,
        ArgumentValidator argumentValidator,
        TransformExecutor executor) {
      return new MysqlTransformRuleDefinition(
          code,
          displayName,
          description,
          ArgumentMode.REQUIRED,
          argumentHint,
          argumentValidator,
          executor);
    }

    void validateArgument(String argument) {
      if (argumentMode == ArgumentMode.REQUIRED && (argument == null || argument.isBlank())) {
        throw new IllegalArgumentException("transformRules.argument is required for type " + code);
      }
      if (argumentMode == ArgumentMode.NONE) {
        return;
      }
      argumentValidator.validate(argument);
    }
  }
}
