package xiaowu.example.supplieretl.datasource.application.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.application.support.IngestionRequestMapper;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceConnection;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJob;
import xiaowu.example.supplieretl.datasource.domain.model.ExcelIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.repository.DataSourceConnectionRepository;
import xiaowu.example.supplieretl.datasource.domain.repository.IngestionJobRepository;
import xiaowu.example.supplieretl.datasource.infrastructure.ingestion.ExcelDataIngester;

@Service
public class IngestionJobApplicationService {

  private final DataSourceConnectionRepository connectionRepository;
  private final IngestionJobRepository jobRepository;
  private final IngestionRequestMapper requestMapper;
  private final Map<DataSourceType, DataIngester> ingesters;
  private final ExcelDataIngester excelDataIngester;

  public IngestionJobApplicationService(
      DataSourceConnectionRepository connectionRepository,
      IngestionJobRepository jobRepository,
      IngestionRequestMapper requestMapper,
      List<DataIngester> ingesters,
      ExcelDataIngester excelDataIngester) {
    this.connectionRepository = Objects.requireNonNull(connectionRepository);
    this.jobRepository = Objects.requireNonNull(jobRepository);
    this.requestMapper = Objects.requireNonNull(requestMapper);
    this.ingesters = indexIngesters(ingesters);
    this.excelDataIngester = Objects.requireNonNull(excelDataIngester);
  }

  /**
   * 针对已保存连接触发一次数据导入（同步执行）
   */
  public IngestionJob execute(Long connectionId, JsonNode requestJson) {
    DataSourceConnection connection = connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

    DataSourceType type = connection.getType();
    if (type == DataSourceType.EXCEL) {
      throw new IllegalArgumentException("Excel ingestion requires file upload, use executeExcel() instead");
    }

    IngestionRequest request = requestJson == null || requestJson.isNull()
        ? requestMapper.requestFromJson(type, null)
        : requestMapper.requestFromJsonNode(type, requestJson);

    IngestionJob job = IngestionJob.create(connectionId, type, request);
    job = jobRepository.save(job);

    return doExecute(job, connection, request);
  }

  /**
   * Excel 文件导入（不需要已保存连接）
   */
  public IngestionJob executeExcel(MultipartFile file, JsonNode requestJson) {
    ExcelIngestionRequest request = requestJson == null || requestJson.isNull()
        ? new ExcelIngestionRequest(null, null, null)
        : (ExcelIngestionRequest) requestMapper.requestFromJsonNode(DataSourceType.EXCEL, requestJson);

    IngestionJob job = IngestionJob.create(null, DataSourceType.EXCEL, request);
    job = jobRepository.save(job);

    job.markRunning();
    jobRepository.update(job);

    try {
      IngestionResult result = excelDataIngester.ingestFile(file, request);
      String message = "Successfully ingested " + result.totalRecords() + " records from Excel";
      job.markCompleted(message, result);
      jobRepository.update(job);
      return job;
    } catch (Exception ex) {
      job.markFailed("Excel ingestion failed: " + ex.getMessage());
      jobRepository.update(job);
      return job;
    }
  }

  public IngestionJob getJob(Long jobId) {
    return jobRepository.findById(jobId)
        .orElseThrow(() -> new IllegalArgumentException("Ingestion job not found: " + jobId));
  }

  public List<IngestionJob> recentJobs(Long connectionId, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return jobRepository.findRecentByConnectionId(connectionId, limit);
  }

  private IngestionJob doExecute(IngestionJob job, DataSourceConnection connection, IngestionRequest request) {
    job.markRunning();
    jobRepository.update(job);

    try {
      DataIngester ingester = requireIngester(connection.getType());
      IngestionResult result = ingester.ingest(connection.getConfig(), request);
      String message = "Successfully ingested " + result.totalRecords()
          + " records from " + connection.getType().name().toLowerCase()
          + " connection '" + connection.getConnectionName() + "'";
      job.markCompleted(message, result);
      jobRepository.update(job);
      return job;
    } catch (Exception ex) {
      job.markFailed(connection.getType().name() + " ingestion failed: " + ex.getMessage());
      jobRepository.update(job);
      return job;
    }
  }

  private DataIngester requireIngester(DataSourceType type) {
    DataIngester ingester = ingesters.get(type);
    if (ingester == null) {
      throw new IllegalStateException("No data ingester configured for type: " + type);
    }
    return ingester;
  }

  private static Map<DataSourceType, DataIngester> indexIngesters(List<DataIngester> ingesters) {
    Map<DataSourceType, DataIngester> indexed = new EnumMap<>(DataSourceType.class);
    for (DataIngester ingester : ingesters) {
      DataIngester previous = indexed.put(ingester.supports(), ingester);
      if (previous != null) {
        throw new IllegalStateException("Duplicate data ingester for type: " + ingester.supports());
      }
    }
    return Map.copyOf(indexed);
  }
}
