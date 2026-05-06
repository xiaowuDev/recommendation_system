package xiaowu.example.Kafka_Idempotent_Demo.domain.exception;

/**
 * 可恢复的 ETL 异常（瞬时失败，应释放幂等键并重投）。
 *
 * <p>触发场景：
 * <ul>
 *   <li>下游数据库瞬时不可达（连接池满、主从切换中）</li>
 *   <li>第三方依赖超时</li>
 *   <li>Redis 暂时连接失败</li>
 * </ul>
 *
 * <p>处理策略：消费者捕获后<b>不 ACK</b>，让 Spring Kafka 的
 * {@code DefaultErrorHandler} 按指数退避重试；同时<b>释放幂等键</b>，
 * 否则重试时会被幂等屏障拦截。
 */
public class TransientEtlException extends RuntimeException {

  public TransientEtlException(String message, Throwable cause) {
    super(message, cause);
  }
}
