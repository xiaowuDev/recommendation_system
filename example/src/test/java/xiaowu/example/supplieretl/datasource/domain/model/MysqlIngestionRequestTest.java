package xiaowu.example.supplieretl.datasource.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
