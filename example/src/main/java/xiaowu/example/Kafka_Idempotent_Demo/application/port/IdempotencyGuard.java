package xiaowu.example.Kafka_Idempotent_Demo.application.port;

import xiaowu.example.Kafka_Idempotent_Demo.domain.model.EventId;

/**
 * 幂等屏障端口（六边形架构中的「驱动端口」）。
 *
 * <p>领域层和应用层只感知"是否已处理过该事件"这个能力本身，
 * 不关心实现是 Redis、数据库唯一索引、Bloom Filter 还是其他。
 *
 * <p>该接口的实现必须满足：
 * <ol>
 *   <li><b>原子性</b>：{@link #tryAcquire} 必须原子地完成"判断 + 占坑"，
 *       不能存在两个并发调用都返回 true 的情况</li>
 *   <li><b>持久性</b>：占坑记录的 TTL 必须大于等于 Kafka 消息保留期</li>
 *   <li><b>可释放</b>：仅在确认是<b>瞬时失败</b>时才释放，永久失败保留占坑</li>
 * </ol>
 */
public interface IdempotencyGuard {

  /**
   * 尝试占用幂等位。
   *
   * @param topic   消息所在 topic（用于按 topic 隔离命名空间）
   * @param eventId 业务唯一 ID
   * @return true = 当前线程是首次处理者，应继续；
   *         false = 已被处理过或正在处理，应跳过
   */
  boolean tryAcquire(String topic, EventId eventId);

  /**
   * 释放幂等位（仅瞬时失败时调用）。
   *
   * <p>注意：永久失败<b>不要</b>调用此方法，否则同一条毒消息会无限重试。
   *
   * @param topic   消息所在 topic
   * @param eventId 业务唯一 ID
   */
  void release(String topic, EventId eventId);
}
