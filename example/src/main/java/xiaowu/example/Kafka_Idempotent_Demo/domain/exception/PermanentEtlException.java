package xiaowu.example.Kafka_Idempotent_Demo.domain.exception;

/**
 * 不可恢复的 ETL 异常（永久失败，应进死信队列）。
 *
 * <p>触发场景：
 * <ul>
 *   <li>消息 Schema 错误（字段缺失、类型不符）</li>
 *   <li>业务数据非法（负金额、未知币种）</li>
 *   <li>违反领域不变量</li>
 * </ul>
 *
 * <p>处理策略：消费者捕获后<b>不要重试</b>，直接 ACK + 转 DLT，
 * 否则同一条毒消息会无限阻塞分区。
 */
public class PermanentEtlException extends RuntimeException {

  public PermanentEtlException(String message) {
    super(message);
  }

  public PermanentEtlException(String message, Throwable cause) {
    super(message, cause);
  }
}
