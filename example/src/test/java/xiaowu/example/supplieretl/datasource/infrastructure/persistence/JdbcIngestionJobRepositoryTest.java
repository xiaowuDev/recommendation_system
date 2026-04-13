package xiaowu.example.supplieretl.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import xiaowu.example.supplieretl.datasource.application.support.IngestionRequestMapper;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJob;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJobStatus;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.KafkaIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.RedisIngestionRequest;

class JdbcIngestionJobRepositoryTest {

  private JdbcIngestionJobRepository repository;

  @BeforeEach
  void setUp() {
    String dbName = "etl_ingestion_" + UUID.randomUUID();
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "sa", "");
    dataSource.setDriverClassName("org.h2.Driver");

    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("""
        CREATE TABLE etl_ingestion_job (
            id               BIGINT AUTO_INCREMENT PRIMARY KEY,
            connection_id    BIGINT        NOT NULL,
            data_source_type VARCHAR(32)   NOT NULL,
            status           VARCHAR(32)   NOT NULL,
            message          VARCHAR(512),
            request_json     CLOB,
            result_json      CLOB,
            started_at       TIMESTAMP     NOT NULL,
            finished_at      TIMESTAMP
        )
        """);

    repository = new JdbcIngestionJobRepository(
        jdbcTemplate, new IngestionRequestMapper(new ObjectMapper()));
  }

  @Test
  void saveAndFindById() {
    KafkaIngestionRequest request = new KafkaIngestionRequest(50, 5000);
    IngestionJob job = IngestionJob.create(1L, DataSourceType.KAFKA, request);

    IngestionJob saved = repository.save(job);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
    assertThat(saved.getConnectionId()).isEqualTo(1L);

    IngestionJob loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getDataSourceType()).isEqualTo(DataSourceType.KAFKA);
    assertThat(loaded.getRequest()).isInstanceOf(KafkaIngestionRequest.class);

    KafkaIngestionRequest loadedRequest = (KafkaIngestionRequest) loaded.getRequest();
    assertThat(loadedRequest.maxRecords()).isEqualTo(50);
    assertThat(loadedRequest.timeoutMs()).isEqualTo(5000);
  }

  @Test
  void updateShouldPersistStatusAndResult() {
    IngestionJob job = IngestionJob.create(2L, DataSourceType.MYSQL,
        new MysqlIngestionRequest("SELECT * FROM test_table", 100));
    IngestionJob saved = repository.save(job);

    saved.markRunning();
    repository.update(saved);

    IngestionResult result = new IngestionResult(10, List.of("id", "name"),
        List.of(), java.util.Map.of("queryTimeMs", 150));
    saved.markCompleted("Done", result);
    repository.update(saved);

    IngestionJob loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getStatus()).isEqualTo(IngestionJobStatus.COMPLETED);
    assertThat(loaded.getMessage()).isEqualTo("Done");
    assertThat(loaded.getResult()).isNotNull();
    assertThat(loaded.getResult().totalRecords()).isEqualTo(10);
    assertThat(loaded.getFinishedAt()).isNotNull();
  }

  @Test
  void findRecentByConnectionId() {
    for (int i = 0; i < 5; i++) {
      repository.save(IngestionJob.create(10L, DataSourceType.REDIS,
          new RedisIngestionRequest("test:*", null, null)));
    }
    repository.save(IngestionJob.create(20L, DataSourceType.KAFKA,
        new KafkaIngestionRequest(null, null)));

    List<IngestionJob> jobs = repository.findRecentByConnectionId(10L, 3);
    assertThat(jobs).hasSize(3);
    assertThat(jobs).allSatisfy(j -> assertThat(j.getConnectionId()).isEqualTo(10L));
  }
}
