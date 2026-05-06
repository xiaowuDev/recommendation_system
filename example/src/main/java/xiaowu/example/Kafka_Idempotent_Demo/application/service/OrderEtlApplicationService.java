package xiaowu.example.Kafka_Idempotent_Demo.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaowu.example.Kafka_Idempotent_Demo.application.port.DwhOrderRepository;
import xiaowu.example.Kafka_Idempotent_Demo.application.port.IdempotencyGuard;
import xiaowu.example.Kafka_Idempotent_Demo.domain.exception.PermanentEtlException;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.DwhOrder;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.OrderEvent;

/**
 * 订单 ETL 应用服务（六边形架构中的「应用层」）。
 *
 * <p>职责：编排 ETL 业务流程的<b>三个阶段</b>：
 * <ol>
 *   <li><b>幂等检查</b>：通过 {@link IdempotencyGuard} 拦截重复消息</li>
 *   <li><b>领域转换</b>：将 {@link OrderEvent} 转换为数仓领域对象 {@link DwhOrder}（含汇率换算）</li>
 *   <li><b>持久化</b>：通过 {@link DwhOrderRepository} 落地到数仓</li>
 * </ol>
 *
 * <p>事务边界：方法内的 DB 操作与事务管理器绑定（{@code @Transactional}），
 * 但 Kafka offset 的提交<b>不参与</b>此事务——offset 由消费者侧的 {@code Acknowledgment}
 * 在应用服务返回成功后手动提交，保证"业务持久化先于 offset 提交"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEtlApplicationService {

  /** 占位的汇率表（生产环境应从配置中心 / 数据库取）。 */
  private static final Map<String, BigDecimal> FX_TO_CNY = Map.of(
      "CNY", BigDecimal.ONE,
      "USD", new BigDecimal("7.20"),
      "EUR", new BigDecimal("7.85"),
      "JPY", new BigDecimal("0.048"));

  private final IdempotencyGuard idempotencyGuard;
  private final DwhOrderRepository dwhOrderRepository;
  private final Clock clock;

  /**
   * 处理一条订单事件。
   *
   * <p>返回值传达"是否应 ACK"的语义：
   * <ul>
   *   <li>{@link Outcome#PROCESSED}：首次处理成功，正常 ACK</li>
   *   <li>{@link Outcome#DUPLICATE}：重复消息，已被幂等屏障拦截，仍需 ACK 防止反复重投</li>
   * </ul>
   *
   * <p>抛出异常时由调用方（消费者）按异常类型决定 ACK 还是重投：
   * <ul>
   *   <li>{@link PermanentEtlException}：消费者应 ACK + 转 DLT</li>
   *   <li>{@link xiaowu.example.Kafka_Idempotent_Demo.domain.exception.TransientEtlException}：
   *       消费者应不 ACK 让框架重试</li>
   * </ul>
   *
   * @param event Kafka 消息反序列化后的领域事件
   * @param topic 来源 topic（用于幂等键命名空间）
   * @return 处理结果
   */
  @Transactional(rollbackFor = Exception.class)
  public Outcome process(OrderEvent event, String topic) {
    // ① 幂等检查 —— 第一道防线（Redis SETNX，O(1) 高性能）
    if (!idempotencyGuard.tryAcquire(topic, event.eventId())) {
      log.info("[OrderEtl] 跳过重复事件 eventId={} orderId={}",
          event.eventId(), event.orderId());
      return Outcome.DUPLICATE;
    }

    try {
      // ② 领域转换：币种 → CNY，附加 ETL 时间戳
      DwhOrder dwhOrder = transform(event);

      // ③ 持久化：仓储实现侧靠主键 upsert 兜底（第二道防线）
      dwhOrderRepository.upsert(dwhOrder);

      log.info("[OrderEtl] 处理成功 eventId={} orderId={} amountCny={}",
          event.eventId(), dwhOrder.orderId(), dwhOrder.amountCny());
      return Outcome.PROCESSED;
    } catch (PermanentEtlException e) {
      // 永久失败：不释放幂等键（避免毒消息无限重试）
      // 调用方应捕获后转 DLT
      throw e;
    } catch (RuntimeException e) {
      // 瞬时失败：释放幂等键，让消息可被框架重投
      idempotencyGuard.release(topic, event.eventId());
      throw e;
    }
  }

  /**
   * 领域转换：将订单事件按业务规则转为数仓记录。
   *
   * <p>这里的逻辑属于<b>领域服务</b>语义——可能未来抽到独立的
   * Domain Service 类，目前内联在应用服务中保持示例简洁。
   */
  private DwhOrder transform(OrderEvent event) {
    BigDecimal rate = FX_TO_CNY.get(event.currency());
    if (rate == null) {
      throw new PermanentEtlException("不支持的币种: " + event.currency());
    }
    BigDecimal amountCny = event.amount()
        .multiply(rate)
        .setScale(2, RoundingMode.HALF_UP);

    return new DwhOrder(
        event.orderId(),
        event.userId(),
        event.status(),
        amountCny,
        event.amount(),
        event.currency(),
        event.occurredAt(),
        Instant.now(clock));
  }

  /** 处理结果（值对象）。 */
  public enum Outcome {
    /** 首次处理成功。 */
    PROCESSED,
    /** 重复消息，已被幂等屏障拦截。 */
    DUPLICATE
  }
}
