package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.DwhOrder;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.OrderStatus;

/**
 * 数仓订单表（JPA 实体）。
 *
 * <p>主键 = {@code order_id}：保证<b>同一订单只有一行</b>，
 * 即使消息重复消费、Redis 失效，JPA 的 {@code save()} 会按主键 upsert，
 * 数仓最终状态依然唯一确定。
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "dwh_orders")
public class DwhOrderEntity {

  @Id
  @Column(name = "order_id", length = 64, nullable = false)
  private String orderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private OrderStatus status;

  @Column(name = "amount_cny", precision = 18, scale = 2, nullable = false)
  private BigDecimal amountCny;

  @Column(name = "original_amount", precision = 18, scale = 2, nullable = false)
  private BigDecimal originalAmount;

  @Column(name = "original_ccy", length = 8, nullable = false)
  private String originalCcy;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "etl_at", nullable = false)
  private Instant etlAt;

  /** 从领域对象构造 JPA 实体。 */
  public static DwhOrderEntity from(DwhOrder order) {
    return new DwhOrderEntity(
        order.orderId(),
        order.userId(),
        order.status(),
        order.amountCny(),
        order.originalAmount(),
        order.originalCcy(),
        order.occurredAt(),
        order.etlAt());
  }
}
