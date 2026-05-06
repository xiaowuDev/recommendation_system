package xiaowu.example.supplieretl.datasource.infrastructure.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;

class MysqlDataIngesterTest {

  @Test
  void shouldMultiplyPriceToCents() {
    assertThat(invokeTransformValue(new BigDecimal("2499.00"), "MULTIPLY", "100"))
        .isEqualTo(249900L);
  }

  @Test
  void shouldRoundHalfUpToInteger() {
    assertThat(invokeTransformValue(new BigDecimal("2499.50"), "ROUND", "0"))
        .isEqualTo(2500L);
  }

  @Test
  void shouldRejectNonNumericArgumentForMultiply() {
    assertThatThrownBy(() -> invokeTransformValue(new BigDecimal("2499.00"), "MULTIPLY", "abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be numeric");
  }

  @Test
  void shouldApplyTransformUsingMappedTargetFieldWhenRuleUsesSourceField() {
    List<Map<String, Object>> rows = List.of(new LinkedHashMap<>(Map.of("price_cents", new BigDecimal("2499.00"))));
    List<MysqlIngestionRequest.FieldMappingRule> mappings = List.of(
        new MysqlIngestionRequest.FieldMappingRule("price", "price_cents", true));
    List<MysqlIngestionRequest.TransformRule> rules = List.of(
        new MysqlIngestionRequest.TransformRule("price", "price_cents", "MULTIPLY", "100", true));

    List<Map<String, Object>> transformedRows = invokeApplyTransformRules(rows, rules, mappings);

    assertThat(transformedRows).singleElement()
        .extracting(row -> row.get("price_cents"))
        .isEqualTo(249900L);
  }

  private Object invokeTransformValue(Object rawValue, String transformType, String argument) {
    try {
      Method method = MysqlDataIngester.class.getDeclaredMethod(
          "transformValue",
          Object.class,
          String.class,
          String.class);
      method.setAccessible(true);
      return method.invoke(null, rawValue, transformType, argument);
    } catch (InvocationTargetException ex) {
      if (ex.getTargetException() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(ex.getTargetException());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> invokeApplyTransformRules(
      List<Map<String, Object>> rows,
      List<MysqlIngestionRequest.TransformRule> rules,
      List<MysqlIngestionRequest.FieldMappingRule> mappings) {
    try {
      Method method = MysqlDataIngester.class.getDeclaredMethod(
          "applyTransformRules",
          List.class,
          List.class,
          List.class);
      method.setAccessible(true);
      return (List<Map<String, Object>>) method.invoke(null, rows, rules, mappings);
    } catch (InvocationTargetException ex) {
      if (ex.getTargetException() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(ex.getTargetException());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
