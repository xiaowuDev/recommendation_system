package xiaowu.example.supplieretl.datasource.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import xiaowu.example.supplieretl.datasource.application.support.IngestionRequestMapper;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJob;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJobStatus;
import xiaowu.example.supplieretl.datasource.domain.repository.IngestionJobRepository;

@Repository
public class JdbcIngestionJobRepository implements IngestionJobRepository {

  private static final String INSERT_SQL = """
      INSERT INTO etl_ingestion_job (
          connection_id,
          data_source_type,
          status,
          message,
          request_json,
          result_json,
          started_at,
          finished_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private static final String UPDATE_SQL = """
      UPDATE etl_ingestion_job
      SET status = ?,
          message = ?,
          result_json = ?,
          finished_at = ?
      WHERE id = ?
      """;

  private static final String FIND_BY_ID_SQL = """
      SELECT id, connection_id, data_source_type, status,
             message, request_json, result_json, started_at, finished_at
      FROM etl_ingestion_job
      WHERE id = ?
      """;

  private static final String FIND_RECENT_SQL = """
      SELECT id, connection_id, data_source_type, status,
             message, request_json, result_json, started_at, finished_at
      FROM etl_ingestion_job
      WHERE connection_id = ?
      ORDER BY started_at DESC, id DESC
      LIMIT ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private final IngestionRequestMapper requestMapper;
  private final RowMapper<IngestionJob> rowMapper = new IngestionJobRowMapper();

  public JdbcIngestionJobRepository(
      JdbcTemplate jdbcTemplate,
      IngestionRequestMapper requestMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.requestMapper = requestMapper;
  }

  @Override
  public IngestionJob save(IngestionJob job) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(con -> {
      PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
      setNullableLong(ps, 1, job.getConnectionId());
      ps.setString(2, job.getDataSourceType().name());
      ps.setString(3, job.getStatus().name());
      ps.setString(4, job.getMessage());
      ps.setString(5, job.getRequest() != null ? requestMapper.requestToJson(job.getRequest()) : null);
      ps.setString(6, job.getResult() != null ? requestMapper.resultToJson(job.getResult()) : null);
      ps.setTimestamp(7, toTimestamp(job.getStartedAt()));
      ps.setTimestamp(8, toTimestamp(job.getFinishedAt()));
      return ps;
    }, keyHolder);

    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to obtain generated id for ingestion job");
    }

    return IngestionJob.restore(
        key.longValue(),
        job.getConnectionId(),
        job.getDataSourceType(),
        job.getStatus(),
        job.getMessage(),
        job.getRequest(),
        job.getResult(),
        job.getStartedAt(),
        job.getFinishedAt());
  }

  @Override
  public IngestionJob update(IngestionJob job) {
    if (job.getId() == null) {
      throw new IllegalArgumentException("Cannot update job without id");
    }
    jdbcTemplate.update(
        UPDATE_SQL,
        job.getStatus().name(),
        job.getMessage(),
        job.getResult() != null ? requestMapper.resultToJson(job.getResult()) : null,
        toTimestamp(job.getFinishedAt()),
        job.getId());
    return job;
  }

  @Override
  public Optional<IngestionJob> findById(Long id) {
    List<IngestionJob> results = jdbcTemplate.query(FIND_BY_ID_SQL, rowMapper, id);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  @Override
  public List<IngestionJob> findRecentByConnectionId(Long connectionId, int limit) {
    return jdbcTemplate.query(FIND_RECENT_SQL, rowMapper, connectionId, limit);
  }

  private static Timestamp toTimestamp(LocalDateTime value) {
    return value == null ? null : Timestamp.valueOf(value);
  }

  private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
    if (value == null) {
      ps.setNull(index, Types.BIGINT);
    } else {
      ps.setLong(index, value);
    }
  }

  private final class IngestionJobRowMapper implements RowMapper<IngestionJob> {

    @Override
    public IngestionJob mapRow(ResultSet rs, int rowNum) throws SQLException {
      DataSourceType type = DataSourceType.valueOf(rs.getString("data_source_type"));
      String requestJson = rs.getString("request_json");
      String resultJson = rs.getString("result_json");
      return IngestionJob.restore(
          rs.getLong("id"),
          rs.getLong("connection_id"),
          type,
          IngestionJobStatus.valueOf(rs.getString("status")),
          rs.getString("message"),
          requestJson != null ? requestMapper.requestFromJson(type, requestJson) : null,
          resultJson != null ? requestMapper.resultFromJson(resultJson) : null,
          toLocalDateTime(rs.getTimestamp("started_at")),
          toLocalDateTime(rs.getTimestamp("finished_at")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
      return timestamp == null ? null : timestamp.toLocalDateTime();
    }
  }
}
