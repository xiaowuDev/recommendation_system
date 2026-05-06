package xiaowu.example.Kafka_Idempotent_Demo.domain.model;

/**
 * 订单状态枚举（领域语义）。
 *
 * <p>对应上游订单系统发出的事件类型：
 * 创建 → 支付 → 发货 → 完成 / 取消。
 */
public enum OrderStatus {
  /** 订单已创建，未支付。 */
  CREATED,
  /** 订单已支付，待发货。 */
  PAID,
  /** 已发货，运输中。 */
  SHIPPED,
  /** 订单完成。 */
  COMPLETED,
  /** 订单取消。 */
  CANCELLED
}
