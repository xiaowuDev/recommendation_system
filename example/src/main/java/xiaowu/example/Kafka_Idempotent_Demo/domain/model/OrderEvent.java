package xiaowu.example.Kafka_Idempotent_Demo.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 订单事件（领域聚合根 / 不可变值）。
 *
 * <p>这是从 Kafka {@code etl.order.events} 主题接收的业务事件，
 * 经过反序列化后承载所有 ETL 所需字段。整个对象一旦构造完成不可修改，
 * 与 Kafka 的 ConsumerRecord 解耦——领域层不依赖任何 Kafka 类。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link #eventId} 是<b>业务唯一</b>的幂等键，由生产者生成</li>
 *   <li>{@link #occurredAt} 是事件在源系统发生的时间，与 Kafka 时间戳无关</li>
 *   <li>使用 record 简化构造，并通过紧凑构造器做不变量校验</li>
 * </ul>
 *
 * @param eventId    业务唯一 ID（幂等键）
 * @param orderId    订单业务 ID（聚合标识）
 * @param userId     下单用户 ID
 * @param status     订单当前状态
 * @param amount     订单金额（精确到分，使用 BigDecimal 避免精度丢失）
 * @param currency   币种 ISO 4217 代码（如 CNY、USD）
 * @param occurredAt 事件在源系统发生的时间
 */
public record OrderEvent(
    EventId eventId,
    String orderId,
    Long userId,
    OrderStatus status,
    BigDecimal amount,
    String currency,
    Instant occurredAt) {

  public OrderEvent {
    Objects.requireNonNull(eventId, "eventId 必填");
    Objects.requireNonNull(orderId, "orderId 必填");
    Objects.requireNonNull(userId, "userId 必填");
    Objects.requireNonNull(status, "status 必填");
    Objects.requireNonNull(amount, "amount 必填");
    Objects.requireNonNull(currency, "currency 必填");
    Objects.requireNonNull(occurredAt, "occurredAt 必填");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("订单金额不能为负数: " + amount);
    }
  }
}
