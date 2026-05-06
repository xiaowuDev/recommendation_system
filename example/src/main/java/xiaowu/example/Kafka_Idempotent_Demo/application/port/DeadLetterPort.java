package xiaowu.example.Kafka_Idempotent_Demo.application.port;

/**
 * 死信端口（DLT）。
 *
 * <p>当消息出现<b>不可恢复</b>的失败（如 Schema 错误、业务非法数据）时，
 * 应用服务通过此端口将消息投递到死信主题，供运维人工排查 / 重放。
 *
 * <p>不进死信的后果：错误消息阻塞所在分区，导致后续正常消息全部停摆。
 */
public interface DeadLetterPort {

  /**
   * 发送消息到死信主题。
   *
   * @param originalTopic 原 topic
   * @param rawPayload    原始消息体（建议截断到 ≤ 4KB，防止超大消息撑爆 Kafka）
   * @param errorReason   失败原因（异常类名 + 简短消息）
   */
  void sendToDeadLetter(String originalTopic, String rawPayload, String errorReason);
}
