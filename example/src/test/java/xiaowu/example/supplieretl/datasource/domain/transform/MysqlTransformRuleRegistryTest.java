package xiaowu.example.supplieretl.datasource.domain.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MysqlTransformRuleRegistryTest {

  private static final MysqlTransformRuleRegistry REGISTRY = MysqlTransformRuleRegistry.defaultRegistry();

  @Test
  void shouldExposeSupportedRules() {
    assertThat(REGISTRY.supportedRuleCodes())
        .contains("TRIM", "MULTIPLY", "ROUND");
  }

  @Test
  void shouldExposeBuiltInCapabilityMetadata() {
    assertThat(REGISTRY.capabilities())
        .extracting(
            MysqlTransformRuleRegistry.MysqlTransformRuleCapability::code,
            MysqlTransformRuleRegistry.MysqlTransformRuleCapability::argumentMode,
            MysqlTransformRuleRegistry.MysqlTransformRuleCapability::argumentHint)
        .contains(
            tuple("TRIM", "NONE", null),
            tuple("ROUND", "OPTIONAL", "Scale, for example 0 or 2"),
            tuple("MULTIPLY", "REQUIRED", "Numeric multiplier"));
  }

  @Test
  void shouldApplyMultiplyRule() {
    assertThat(REGISTRY.apply("MULTIPLY", new BigDecimal("24.99"), "100"))
        .isEqualTo(2499L);
  }

  @Test
  void shouldRejectMissingArgumentForRequiredRule() {
    assertThatThrownBy(() -> REGISTRY.validateRule("MULTIPLY", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("transformRules.argument is required");
  }

  @Test
  void shouldAllowRoundWithoutArgument() {
    REGISTRY.validateRule("ROUND", null);
    assertThat(REGISTRY.apply("ROUND", new BigDecimal("24.99"), null))
        .isEqualTo(25L);
  }
}
