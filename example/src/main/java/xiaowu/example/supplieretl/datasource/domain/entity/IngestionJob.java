package xiaowu.example.supplieretl.datasource.domain.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;

/**
 * 数据接入任务实体
 */
public final class IngestionJob {

  private final Long id;
  private final Long connectionId;
  private final DataSourceType dataSourceType;
  private IngestionJobStatus status;
  private String message;
  private final IngestionRequest request;
  private IngestionResult result;
  private final LocalDateTime startedAt;
  private LocalDateTime finishedAt;

  private IngestionJob(
      Long id,
      Long connectionId,
      DataSourceType dataSourceType,
      IngestionJobStatus status,
      String message,
      IngestionRequest request,
      IngestionResult result,
      LocalDateTime startedAt,
      LocalDateTime finishedAt) {
    if (id != null && id <= 0) {
      throw new IllegalArgumentException("id must be positive when present");
    }
    if (connectionId != null && connectionId <= 0) {
      throw new IllegalArgumentException("connectionId must be positive when present");
    }
    this.id = id;
    this.connectionId = connectionId;
    this.dataSourceType = Objects.requireNonNull(dataSourceType, "dataSourceType must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.message = message;
    this.request = request;
    this.result = result;
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    this.finishedAt = finishedAt;
  }

  /**
   * 创建新的 PENDING 状态任务
   */
  public static IngestionJob create(
      Long connectionId,
      DataSourceType dataSourceType,
      IngestionRequest request) {
    return new IngestionJob(
        null,
        connectionId,
        dataSourceType,
        IngestionJobStatus.PENDING,
        null,
        request,
        null,
        LocalDateTime.now(),
        null);
  }

  /**
   * 从持久化层恢复
   */
  public static IngestionJob restore(
      Long id,
      Long connectionId,
      DataSourceType dataSourceType,
      IngestionJobStatus status,
      String message,
      IngestionRequest request,
      IngestionResult result,
      LocalDateTime startedAt,
      LocalDateTime finishedAt) {
    return new IngestionJob(
        id, connectionId, dataSourceType, status,
        message, request, result, startedAt, finishedAt);
  }

  public void markRunning() {
    this.status = IngestionJobStatus.RUNNING;
  }

  public void markCompleted(String message, IngestionResult result) {
    this.status = IngestionJobStatus.COMPLETED;
    this.message = message;
    this.result = result;
    this.finishedAt = LocalDateTime.now();
  }

  public void markFailed(String message) {
    this.status = IngestionJobStatus.FAILED;
    this.message = message;
    this.finishedAt = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public Long getConnectionId() { return connectionId; }
  public DataSourceType getDataSourceType() { return dataSourceType; }
  public IngestionJobStatus getStatus() { return status; }
  public String getMessage() { return message; }
  public IngestionRequest getRequest() { return request; }
  public IngestionResult getResult() { return result; }
  public LocalDateTime getStartedAt() { return startedAt; }
  public LocalDateTime getFinishedAt() { return finishedAt; }
}
