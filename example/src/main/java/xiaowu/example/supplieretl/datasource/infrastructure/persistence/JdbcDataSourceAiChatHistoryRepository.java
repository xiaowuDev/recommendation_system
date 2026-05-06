package xiaowu.example.supplieretl.datasource.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import xiaowu.example.supplieretl.datasource.application.support.IngestionRequestMapper;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceAiChatHistory;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.repository.DataSourceAiChatHistoryRepository;

@Repository
public class JdbcDataSourceAiChatHistoryRepository implements DataSourceAiChatHistoryRepository {

  private static final String INSERT_SQL = """
      INSERT INTO etl_ai_chat_history (
          connection_id,
          session_id,
          user_message,
          assistant_message,
          source_summary,
          suggestion_available,
          suggested_request_json,
          warnings_json,
          generated_at,
          created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private static final String FIND_BY_CONNECTION_SQL = """
      SELECT id,
             connection_id,
             session_id,
             user_message,
             assistant_message,
             source_summary,
             suggestion_available,
             suggested_request_json,
             warnings_json,
             generated_at,
             created_at
      FROM etl_ai_chat_history
      WHERE connection_id = ?
      ORDER BY generated_at DESC, id DESC
      """;

  private static final String FIND_BY_CONNECTION_LIMIT_SQL = FIND_BY_CONNECTION_SQL + "\nLIMIT ?";

  private final JdbcTemplate jdbcTemplate;
  private final IngestionRequestMapper ingestionRequestMapper;
  private final ObjectMapper objectMapper;
  private final RowMapper<DataSourceAiChatHistory> rowMapper = new AiChatHistoryRowMapper();

  public JdbcDataSourceAiChatHistoryRepository(
      JdbcTemplate jdbcTemplate,
      IngestionRequestMapper ingestionRequestMapper,
      ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.ingestionRequestMapper = ingestionRequestMapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public DataSourceAiChatHistory save(DataSourceAiChatHistory history) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(con -> {
      PreparedStatement statement = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, history.getConnectionId());
      statement.setString(2, history.getSessionId());
      statement.setString(3, history.getUserMessage());
      statement.setString(4, history.getAssistantMessage());
      statement.setString(5, history.getSourceSummary());
      statement.setBoolean(6, history.isSuggestionAvailable());
      statement.setString(
          7,
          history.getSuggestedRequest() == null ? null : ingestionRequestMapper.requestToJson(history.getSuggestedRequest()));
      statement.setString(8, toJson(history.getWarnings()));
      statement.setTimestamp(9, Timestamp.valueOf(history.getGeneratedAt()));
      statement.setTimestamp(10, Timestamp.valueOf(history.getCreatedAt()));
      return statement;
    }, keyHolder);

    Number key = keyHolder.getKey();
    return DataSourceAiChatHistory.restore(
        key == null ? null : key.longValue(),
        history.getConnectionId(),
        history.getSessionId(),
        history.getUserMessage(),
        history.getAssistantMessage(),
        history.getSourceSummary(),
        history.isSuggestionAvailable(),
        history.getSuggestedRequest(),
        history.getWarnings(),
        history.getGeneratedAt(),
        history.getCreatedAt());
  }

  @Override
  public List<DataSourceAiChatHistory> findByConnectionId(Long connectionId, Integer limit) {
    if (limit == null) {
      return jdbcTemplate.query(FIND_BY_CONNECTION_SQL, rowMapper, connectionId);
    }
    return jdbcTemplate.query(FIND_BY_CONNECTION_LIMIT_SQL, rowMapper, connectionId, limit);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize AI chat history payload", ex);
    }
  }

  private final class AiChatHistoryRowMapper implements RowMapper<DataSourceAiChatHistory> {

    @Override
    public DataSourceAiChatHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
      String suggestedRequestJson = rs.getString("suggested_request_json");
      return DataSourceAiChatHistory.restore(
          rs.getLong("id"),
          rs.getLong("connection_id"),
          rs.getString("session_id"),
          rs.getString("user_message"),
          rs.getString("assistant_message"),
          rs.getString("source_summary"),
          rs.getBoolean("suggestion_available"),
          suggestedRequestJson == null || suggestedRequestJson.isBlank()
              ? null
              : (MysqlIngestionRequest) ingestionRequestMapper.requestFromJson(DataSourceType.MYSQL, suggestedRequestJson),
          readWarnings(rs.getString("warnings_json")),
          toLocalDateTime(rs.getTimestamp("generated_at")),
          toLocalDateTime(rs.getTimestamp("created_at")));
    }

    private List<String> readWarnings(String json) {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      try {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
      } catch (JsonProcessingException ex) {
        throw new IllegalStateException("Failed to deserialize AI chat history warnings", ex);
      }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
      return timestamp == null ? null : timestamp.toLocalDateTime();
    }
  }
}
