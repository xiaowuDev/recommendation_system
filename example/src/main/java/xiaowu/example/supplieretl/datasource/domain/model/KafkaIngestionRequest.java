package xiaowu.example.supplieretl.datasource.domain.model;

/**
 * Kafka 数据接入请求参数
 *
 * @param maxRecords 单次拉取的最大消息数，默认 100
 * @param timeoutMs  消费超时时间（毫秒），默认 10000
 */
public record KafkaIngestionRequest(
    Integer maxRecords,
    Integer timeoutMs) implements IngestionRequest {

  private static final int DEFAULT_MAX_RECORDS = 100;
  private static final int DEFAULT_TIMEOUT_MS = 10_000;
  private static final int MAX_RECORDS_LIMIT = 10_000;

  public KafkaIngestionRequest {
    maxRecords = maxRecords == null ? DEFAULT_MAX_RECORDS : maxRecords;
    timeoutMs = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
  }

  @Override
  public void validate() {
    if (maxRecords <= 0 || maxRecords > MAX_RECORDS_LIMIT) {
      throw new IllegalArgumentException("maxRecords must be between 1 and " + MAX_RECORDS_LIMIT);
    }
    if (timeoutMs <= 0 || timeoutMs > 60_000) {
      throw new IllegalArgumentException("timeoutMs must be between 1 and 60000");
    }
  }
}
