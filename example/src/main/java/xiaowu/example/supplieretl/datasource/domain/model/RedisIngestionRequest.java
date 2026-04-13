package xiaowu.example.supplieretl.datasource.domain.model;

/**
 * Redis 数据接入请求参数
 *
 * @param keyPattern SCAN 匹配模式，默认 "*"
 * @param scanCount  每次 SCAN 的 COUNT 参数，默认 100
 * @param maxKeys    最大读取 key 数量，默认 100
 */
public record RedisIngestionRequest(
    String keyPattern,
    Integer scanCount,
    Integer maxKeys) implements IngestionRequest {

  private static final int DEFAULT_SCAN_COUNT = 100;
  private static final int DEFAULT_MAX_KEYS = 100;
  private static final int MAX_KEYS_LIMIT = 10_000;

  public RedisIngestionRequest {
    keyPattern = DataSourceConfigSupport.defaultIfBlank(keyPattern, "*");
    scanCount = scanCount == null ? DEFAULT_SCAN_COUNT : scanCount;
    maxKeys = maxKeys == null ? DEFAULT_MAX_KEYS : maxKeys;
  }

  @Override
  public void validate() {
    if (scanCount <= 0) {
      throw new IllegalArgumentException("scanCount must be positive");
    }
    if (maxKeys <= 0 || maxKeys > MAX_KEYS_LIMIT) {
      throw new IllegalArgumentException("maxKeys must be between 1 and " + MAX_KEYS_LIMIT);
    }
  }
}
