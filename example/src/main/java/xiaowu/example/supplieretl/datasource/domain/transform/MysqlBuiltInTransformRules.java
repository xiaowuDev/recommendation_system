package xiaowu.example.supplieretl.datasource.domain.transform;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

final class MysqlBuiltInTransformRules {

  private static final int DIVIDE_SCALE = 8;

  private MysqlBuiltInTransformRules() {
  }

  /**
   * 定义内置的MySQL转换规则列表，包括每个规则的代码、显示名称、描述、参数模式、参数提示信息以及实际的转换逻辑实现。
   *
   * @return MySQL转换规则定义列表，供MysqlTransformRuleRegistry使用以构建默认的转换规则注册表
   */
  static List<MysqlTransformRuleRegistry.MysqlTransformRuleDefinition> definitions() {
    return List.of(
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.none(
            "TRIM",
            "Trim",
            "Trim surrounding whitespace from a text value.",
            (rawValue, argument) -> rawValue == null ? null : String.valueOf(rawValue).trim()),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.none(
            "UPPERCASE",
            "Uppercase",
            "Convert text to uppercase.",
            (rawValue, argument) -> rawValue == null ? null : String.valueOf(rawValue).toUpperCase(Locale.ROOT)),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.none(
            "LOWERCASE",
            "Lowercase",
            "Convert text to lowercase.",
            (rawValue, argument) -> rawValue == null ? null : String.valueOf(rawValue).toLowerCase(Locale.ROOT)),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "PREFIX",
            "Prefix",
            "Prefix text with the argument value.",
            "Text prefix",
            MysqlBuiltInTransformRules::validateRequiredTextArgument,
            (rawValue, argument) -> rawValue == null ? null : argument + rawText(rawValue)),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "SUFFIX",
            "Suffix",
            "Append the argument value to text.",
            "Text suffix",
            MysqlBuiltInTransformRules::validateRequiredTextArgument,
            (rawValue, argument) -> rawValue == null ? null : rawText(rawValue) + argument),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "REPLACE_NULL",
            "Replace Null",
            "Replace null values with the provided argument.",
            "Fallback value",
            MysqlBuiltInTransformRules::validateRequiredTextArgument,
            (rawValue, argument) -> rawValue == null ? argument : rawValue),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "ADD",
            "Add",
            "Add the numeric argument to the field value.",
            "Numeric value",
            argument -> parseRequiredNumericArgument(argument, "ADD"),
            (rawValue, argument) -> rawValue == null ? null
                : normalizeNumericResult(toBigDecimal(rawValue).add(parseRequiredNumericArgument(argument, "ADD")))),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "SUBTRACT",
            "Subtract",
            "Subtract the numeric argument from the field value.",
            "Numeric value",
            argument -> parseRequiredNumericArgument(argument, "SUBTRACT"),
            (rawValue, argument) -> rawValue == null ? null
                : normalizeNumericResult(
                    toBigDecimal(rawValue).subtract(parseRequiredNumericArgument(argument, "SUBTRACT")))),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "MULTIPLY",
            "Multiply",
            "Multiply the numeric field value by the numeric argument.",
            "Numeric multiplier",
            argument -> parseRequiredNumericArgument(argument, "MULTIPLY"),
            (rawValue, argument) -> rawValue == null ? null
                : normalizeNumericResult(
                    toBigDecimal(rawValue).multiply(parseRequiredNumericArgument(argument, "MULTIPLY")))),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.required(
            "DIVIDE",
            "Divide",
            "Divide the numeric field value by the numeric argument.",
            "Numeric divisor",
            MysqlBuiltInTransformRules::validateDivideArgument,
            (rawValue, argument) -> rawValue == null ? null : divide(rawValue, argument)),
        MysqlTransformRuleRegistry.MysqlTransformRuleDefinition.optional(
            "ROUND",
            "Round",
            "Round the numeric field value to the provided integer scale. If omitted, scale 0 is used.",
            "Scale, for example 0 or 2",
            MysqlBuiltInTransformRules::validateRoundArgument,
            (rawValue, argument) -> rawValue == null ? null : round(rawValue, argument)));
  }

  /**
   * 验证必需的文本参数，如果参数缺失或仅包含空白字符，则抛出IllegalArgumentException异常。
   *
   * @param argument 必需的文本参数
   */
  private static void validateRequiredTextArgument(String argument) {
    if (argument == null || argument.isBlank()) {
      throw new IllegalArgumentException("transformRules.argument is required for this transform type");
    }
  }

  /**
   * 验证DIVIDE转换规则的参数，确保参数是一个非零的数值。如果参数缺失、空白、无法解析为数字，或者为零，则抛出IllegalArgumentException异常。
   *
   * @param argument
   */
  private static void validateDivideArgument(String argument) {
    BigDecimal divisor = parseRequiredNumericArgument(argument, "DIVIDE");
    if (BigDecimal.ZERO.compareTo(divisor) == 0) {
      throw new IllegalArgumentException("transformRules.argument cannot be zero for DIVIDE");
    }
  }

  private static void validateRoundArgument(String argument) {
    if (argument == null || argument.isBlank()) {
      return;
    }
    parseRoundScale(argument);
  }

  private static BigDecimal toBigDecimal(Object rawValue) {
    if (rawValue instanceof BigDecimal decimal) {
      return decimal;
    }
    if (rawValue instanceof BigInteger integer) {
      return new BigDecimal(integer);
    }
    if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer
        || rawValue instanceof Long) {
      return BigDecimal.valueOf(((Number) rawValue).longValue());
    }
    if (rawValue instanceof Float || rawValue instanceof Double) {
      return BigDecimal.valueOf(((Number) rawValue).doubleValue());
    }

    try {
      return new BigDecimal(rawText(rawValue));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Numeric transform requires a numeric field value but got: " + rawValue, ex);
    }
  }

  private static String rawText(Object rawValue) {
    return String.valueOf(rawValue).trim();
  }

  /**
   * 解析必需的数值参数，如果参数缺失或无法解析为数字，则抛出IllegalArgumentException异常。
   *
   * @param argument      参数
   * @param transformType 转换类型，用于错误消息中指示哪个转换规则的参数有问题
   * @return 解析后的BigDecimal数值
   * @throws IllegalArgumentException 如果参数缺失、空白或无法解析为数字
   */
  private static BigDecimal parseRequiredNumericArgument(String argument, String transformType) {
    if (argument == null || argument.isBlank()) {
      throw new IllegalArgumentException("transformRules.argument is required for type " + transformType);
    }
    try {
      return new BigDecimal(argument.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "transformRules.argument must be numeric for type " + transformType + ": " + argument,
          ex);
    }
  }

  /**
   * 解析ROUND转换规则的参数，确保参数是一个整数。如果参数无法解析为整数，则抛出IllegalArgumentException异常。
   *
   * @param argument ROUND转换规则的参数
   * @return 解析后的整数值
   * @throws IllegalArgumentException 如果参数无法解析为整数
   */
  private static int parseRoundScale(String argument) {
    try {
      return Integer.parseInt(argument.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("ROUND transform argument must be an integer scale: " + argument, ex);
    }
  }

  private static Object divide(Object rawValue, String argument) {
    BigDecimal value = toBigDecimal(rawValue)
        .divide(parseRequiredNumericArgument(argument, "DIVIDE"), DIVIDE_SCALE, RoundingMode.HALF_UP);
    return normalizeNumericResult(value);
  }

  private static Object round(Object rawValue, String argument) {
    int scale = argument == null || argument.isBlank() ? 0 : parseRoundScale(argument);
    BigDecimal rounded = toBigDecimal(rawValue).setScale(scale, RoundingMode.HALF_UP);
    return normalizeNumericResult(rounded);
  }

  private static Object normalizeNumericResult(BigDecimal value) {
    BigDecimal normalized = value.stripTrailingZeros();
    if (normalized.scale() <= 0) {
      try {
        return normalized.longValueExact();
      } catch (ArithmeticException ex) {
        return normalized.toBigIntegerExact();
      }
    }
    return normalized;
  }
}
