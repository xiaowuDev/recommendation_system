package xiaowu.example.Kafka_Idempotent_Demo.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 数据仓库订单记录（领域实体）。
 *
 * <p>{@link OrderEvent} 经过 ETL 转换后落地的形态。与上游事件相比的差异：
 * <ul>
 *   <li>金额已统一换算为人民币（CNY）</li>
 *   <li>携带 {@code etlAt} 表示 ETL 处理时间，便于审计</li>
 * </ul>
 *
 * @param orderId        订单业务 ID（数仓主键）
 * @param userId         用户 ID
 * @param status         订单状态
 * @param amountCny      统一转换为 CNY 的金额
 * @param originalAmount 源币种金额（保留追溯信息）
 * @param originalCcy    源币种代码
 * @param occurredAt     源事件发生时间
 * @param etlAt          ETL 处理时间
 */
public record DwhOrder(
    String orderId,
    Long userId,
    OrderStatus status,
    BigDecimal amountCny,
    BigDecimal originalAmount,
    String originalCcy,
    Instant occurredAt,
    Instant etlAt) {
}
