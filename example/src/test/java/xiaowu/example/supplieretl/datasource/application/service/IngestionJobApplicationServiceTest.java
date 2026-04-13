package xiaowu.example.supplieretl.datasource.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.application.support.IngestionRequestMapper;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceConnection;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJob;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJobStatus;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.KafkaDataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.repository.DataSourceConnectionRepository;
import xiaowu.example.supplieretl.datasource.domain.repository.IngestionJobRepository;
import xiaowu.example.supplieretl.datasource.infrastructure.ingestion.ExcelDataIngester;

@ExtendWith(MockitoExtension.class)
class IngestionJobApplicationServiceTest {

  @Mock
  private DataSourceConnectionRepository connectionRepository;

  @Mock
  private IngestionJobRepository jobRepository;

  private IngestionJobApplicationService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service = new IngestionJobApplicationService(
        connectionRepository,
        jobRepository,
        new IngestionRequestMapper(objectMapper),
        List.of(new StubKafkaIngester()),
        new ExcelDataIngester());
  }

  @Test
  void executeShouldCompleteJobOnSuccess() throws Exception {
    DataSourceConnection connection = DataSourceConnection.restore(
        1L, "test-kafka", null,
        DataSourceType.KAFKA,
        new KafkaDataSourceConfig("localhost:9092", "test-topic", null),
        LocalDateTime.now(), LocalDateTime.now());

    when(connectionRepository.findById(1L)).thenReturn(Optional.of(connection));
    when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> {
      IngestionJob job = inv.getArgument(0);
      return IngestionJob.restore(
          100L, job.getConnectionId(), job.getDataSourceType(),
          job.getStatus(), job.getMessage(), job.getRequest(),
          job.getResult(), job.getStartedAt(), job.getFinishedAt());
    });
    when(jobRepository.update(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));

    IngestionJob result = service.execute(1L,
        objectMapper.readTree("{\"maxRecords\": 10, \"timeoutMs\": 5000}"));

    assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.COMPLETED);
    assertThat(result.getResult()).isNotNull();
    assertThat(result.getResult().totalRecords()).isEqualTo(3);
    assertThat(result.getResult().columns()).containsExactly("key", "value");
    assertThat(result.getMessage()).contains("Successfully ingested");
  }

  @Test
  void executeShouldMarkFailedOnError() throws Exception {
    DataSourceConnection connection = DataSourceConnection.restore(
        2L, "fail-kafka", null,
        DataSourceType.KAFKA,
        new KafkaDataSourceConfig("bad-host:9092", "test-topic", null),
        LocalDateTime.now(), LocalDateTime.now());

    when(connectionRepository.findById(2L)).thenReturn(Optional.of(connection));
    when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> {
      IngestionJob job = inv.getArgument(0);
      return IngestionJob.restore(
          101L, job.getConnectionId(), job.getDataSourceType(),
          job.getStatus(), job.getMessage(), job.getRequest(),
          job.getResult(), job.getStartedAt(), job.getFinishedAt());
    });
    when(jobRepository.update(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));

    IngestionJob result = service.execute(2L, objectMapper.readTree("{}"));

    assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
    assertThat(result.getMessage()).contains("ingestion failed");
  }

  @Test
  void executeShouldUseDefaultRequestWhenJsonIsNull() throws Exception {
    DataSourceConnection connection = DataSourceConnection.restore(
        3L, "default-kafka", null,
        DataSourceType.KAFKA,
        new KafkaDataSourceConfig("localhost:9092", "test-topic", null),
        LocalDateTime.now(), LocalDateTime.now());

    when(connectionRepository.findById(3L)).thenReturn(Optional.of(connection));
    when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> {
      IngestionJob job = inv.getArgument(0);
      return IngestionJob.restore(
          102L, job.getConnectionId(), job.getDataSourceType(),
          job.getStatus(), job.getMessage(), job.getRequest(),
          job.getResult(), job.getStartedAt(), job.getFinishedAt());
    });
    when(jobRepository.update(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));

    IngestionJob result = service.execute(3L, null);

    assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.COMPLETED);
  }

  /**
   * Stub Kafka ingester: 如果 bootstrapServers 包含 "bad" 则抛异常，否则返回固定结果
   */
  private static final class StubKafkaIngester implements DataIngester {

    @Override
    public DataSourceType supports() {
      return DataSourceType.KAFKA;
    }

    @Override
    public IngestionResult ingest(DataSourceConfig config, IngestionRequest request) {
      KafkaDataSourceConfig kafkaConfig = (KafkaDataSourceConfig) config;
      if (kafkaConfig.bootstrapServers().contains("bad")) {
        throw new RuntimeException("Connection refused");
      }
      return new IngestionResult(
          3,
          List.of("key", "value"),
          List.of(
              Map.of("key", "k1", "value", "v1"),
              Map.of("key", "k2", "value", "v2"),
              Map.of("key", "k3", "value", "v3")),
          Map.of("topic", kafkaConfig.topic()));
    }
  }
}
