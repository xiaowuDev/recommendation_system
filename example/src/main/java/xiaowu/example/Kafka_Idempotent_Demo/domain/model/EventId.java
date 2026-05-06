package xiaowu.example.Kafka_Idempotent_Demo.domain.model;

import java.util.Objects;

/**
 * 事件业务唯一 ID（值对象）。
 *
 * <p>幂等的核心：必须是<b>业务唯一</b>且<b>跨重试稳定</b>的字符串。
 * 严禁使用 Kafka 的 offset 或 partition 来构造，因为：
 * <ul>
 *   <li>Rebalance / 重启后，offset 会从已提交的位置重新开始读取</li>
 *   <li>同一条业务消息被生产者重发时，offset 会变化，幂等键失效</li>
 * </ul>
 *
 * <p>推荐的生成方式（生产者侧）：
 * <ul>
 *   <li>UUID（最简单，无业务含义）</li>
 *   <li>业务主键拼接：{@code orderId + ":" + version}</li>
 *   <li>事件源 ID：{@code sourceSystem + ":" + sourceEventId}</li>
 * </ul>
 *
 * @param value 业务唯一 ID 字符串，不可为空
 */
public record EventId(String value) {

  public EventId {
    Objects.requireNonNull(value, "EventId.value 不能为 null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("EventId.value 不能为空字符串");
    }
    if (value.length() > 64) {
      throw new IllegalArgumentException("EventId.value 长度不能超过 64");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
