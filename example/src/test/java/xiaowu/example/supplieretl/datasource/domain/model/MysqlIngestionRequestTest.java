package xiaowu.example.supplieretl.datasource.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class MysqlIngestionRequestTest {

  @Test
  void shouldAcceptValidSelectQuery() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        "SELECT * FROM suppliers LIMIT 10", 100);
    request.validate();
    assertThat(request.query()).isEqualTo("SELECT * FROM suppliers LIMIT 10");
    assertThat(request.maxRows()).isEqualTo(100);
  }

  @Test
  void shouldRejectInsertStatement() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        "INSERT INTO suppliers VALUES (1, 'test')", null);
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only SELECT");
  }

  @Test
  void shouldRejectDeleteStatement() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        "DELETE FROM suppliers WHERE id = 1", null);
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only SELECT");
  }

  @Test
  void shouldRejectDropInSelect() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        "SELECT * FROM suppliers; DROP TABLE suppliers", null);
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DROP");
  }

  @Test
  void shouldUseDefaultMaxRows() {
    MysqlIngestionRequest request = new MysqlIngestionRequest("SELECT 1", null);
    assertThat(request.maxRows()).isEqualTo(1000);
  }

  @Test
  void shouldRejectInvalidMaxRows() {
    MysqlIngestionRequest request = new MysqlIngestionRequest("SELECT 1", 0);
    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxRows");
  }

  @Test
  void shouldAcceptNumericTransformTypes() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        null,
        "recommendation.item_catalog",
        List.of(new MysqlIngestionRequest.FieldMappingRule("price", "price", true)),
        List.of(),
        List.of(
            new MysqlIngestionRequest.TransformRule("price", "price_in_cents", "MULTIPLY", "100", true),
            new MysqlIngestionRequest.TransformRule("price_in_cents", "price_in_cents", "ROUND", "0", true)),
        new MysqlIngestionRequest.TargetConfig("item_catalog_export", "APPEND", null, null),
        null,
        null,
        1000);

    request.validate();
    assertThat(request.enabledTransformRules()).hasSize(2);
  }

  @Test
  void shouldRequireArgumentForMultiplyTransform() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        null,
        "recommendation.item_catalog",
        List.of(new MysqlIngestionRequest.FieldMappingRule("price", "price", true)),
        List.of(),
        List.of(new MysqlIngestionRequest.TransformRule("price", "price_in_cents", "MULTIPLY", null, true)),
        new MysqlIngestionRequest.TargetConfig("item_catalog_export", "APPEND", null, null),
        null,
        null,
        1000);

    assertThatThrownBy(request::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("transformRules.argument is required for type MULTIPLY");
  }

  @Test
  void shouldAllowRoundWithoutArgument() {
    MysqlIngestionRequest request = new MysqlIngestionRequest(
        null,
        "recommendation.item_catalog",
        List.of(new MysqlIngestionRequest.FieldMappingRule("price", "price", true)),
        List.of(),
        List.of(new MysqlIngestionRequest.TransformRule("price", "price_rounded", "ROUND", null, true)),
        new MysqlIngestionRequest.TargetConfig("item_catalog_export", "APPEND", null, null),
        null,
        null,
        1000);

    request.validate();
    assertThat(request.enabledTransformRules()).singleElement()
        .extracting(MysqlIngestionRequest.TransformRule::transformType)
        .isEqualTo("ROUND");
  }
}
