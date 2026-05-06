package xiaowu.example.supplieretl.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import xiaowu.example.supplieretl.datasource.application.support.IngestionRequestMapper;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceAiChatHistory;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;

class JdbcDataSourceAiChatHistoryRepositoryTest {

  private JdbcDataSourceAiChatHistoryRepository repository;

  @BeforeEach
  void setUp() {
    String dbName = "etl_ai_chat_history_" + UUID.randomUUID();
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "sa",
        "");
    dataSource.setDriverClassName("org.h2.Driver");

    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("""
        CREATE TABLE etl_ai_chat_history (
            id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
            connection_id          BIGINT        NOT NULL,
            session_id             VARCHAR(128)  NOT NULL,
            user_message           CLOB          NOT NULL,
            assistant_message      CLOB          NOT NULL,
            source_summary         CLOB,
            suggestion_available   BOOLEAN       NOT NULL,
            suggested_request_json CLOB,
            warnings_json          CLOB,
            generated_at           TIMESTAMP     NOT NULL,
            created_at             TIMESTAMP     NOT NULL
        )
        """);

    ObjectMapper objectMapper = new ObjectMapper();
    repository = new JdbcDataSourceAiChatHistoryRepository(
        jdbcTemplate,
        new IngestionRequestMapper(objectMapper),
        objectMapper);
  }

  @Test
  void saveAndFindByConnectionId() {
    LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 15, 14, 30, 0);
    MysqlIngestionRequest suggestedRequest = new MysqlIngestionRequest("SELECT * FROM item_catalog", 200);
    DataSourceAiChatHistory history = DataSourceAiChatHistory.create(
        1L,
        "session-1",
        "inspect item catalog",
        "use item_catalog as the source",
        "item_catalog with sample rows",
        true,
        suggestedRequest,
        List.of("sample rows are partial"),
        generatedAt);

    DataSourceAiChatHistory saved = repository.save(history);
    List<DataSourceAiChatHistory> histories = repository.findByConnectionId(1L, null);

    assertThat(saved.getId()).isNotNull();
    assertThat(histories).hasSize(1);
    assertThat(histories.get(0).getSessionId()).isEqualTo("session-1");
    assertThat(histories.get(0).getSuggestedRequest()).isInstanceOf(MysqlIngestionRequest.class);
    assertThat(histories.get(0).getWarnings()).containsExactly("sample rows are partial");
    assertThat(histories.get(0).getGeneratedAt()).isEqualTo(generatedAt);
  }

  @Test
  void findByConnectionIdShouldRespectLimit() {
    for (int index = 0; index < 4; index++) {
      repository.save(DataSourceAiChatHistory.create(
          9L,
          "session-" + index,
          "prompt-" + index,
          "assistant-" + index,
          null,
          false,
          null,
          List.of(),
          LocalDateTime.of(2026, 4, 15, 10, 0, index)));
    }
    repository.save(DataSourceAiChatHistory.create(
        10L,
        "session-other",
        "other",
        "other",
        null,
        false,
        null,
        List.of(),
        LocalDateTime.of(2026, 4, 15, 10, 1, 0)));

    List<DataSourceAiChatHistory> histories = repository.findByConnectionId(9L, 2);

    assertThat(histories).hasSize(2);
    assertThat(histories)
        .allSatisfy(history -> assertThat(history.getConnectionId()).isEqualTo(9L));
    assertThat(histories.get(0).getSessionId()).isEqualTo("session-3");
  }
}
