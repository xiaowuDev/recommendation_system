package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaowu.example.Kafka_Idempotent_Demo.application.port.IdempotencyGuard;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.EventId;
import xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.entity.ConsumedEventLogEntity;
import xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.repository.ConsumedEventLogJpaRepository;

/**
 * 双层幂等屏障：Redis（一级缓存）+ MySQL 唯一索引（二级兜底）。
 *
 * <p>这是<b>生产环境推荐</b>的实现——单层 Redis 在故障时会失效，
 * 需要 DB 唯一索引兜底剩下 0.x% 的边缘情况。
 *
 * <p>判定流程：
 * <pre>
 *   Redis SETNX
 *     ├─ true  → 写 DB 唯一索引
 *     │           ├─ 成功 → 首次处理，返回 true
 *     │           └─ 唯一键冲突 → 已被其他实例处理，返回 false
 *     └─ false → 已被处理过，直接返回 false
 * </pre>
 *
 * <p>用 {@code @Primary} 标注：当应用同时存在 RedisIdempotencyGuard
 * 和此 Bean 时，Spring 优先注入此双层版本。如只想用 Redis，
 * 可设置 {@code demo.kafka-idempotent.dual-layer=false} 关闭此 Bean。
 */
@Slf4j
@Primary
@Component("dualLayerIdempotencyGuard")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "demo.kafka-idempotent",
    name = {"enabled", "dual-layer"},
    havingValue = "true",
    matchIfMissing = false)
public class DualLayerIdempotencyGuard implements IdempotencyGuard {

  private final RedisIdempotencyGuard redisGuard;
  private final ConsumedEventLogJpaRepository dbRepository;

  @Override
  public boolean tryAcquire(String topic, EventId eventId) {
    // ① 第一层：Redis（拦截 99.9% 的常规重复，O(1) 高性能）
    if (!redisGuard.tryAcquire(topic, eventId)) {
      return false;
    }

    // ② 第二层：DB 唯一索引（应对 Redis 故障 / TTL 失效 / 数据丢失）
    try {
      dbRepository.save(ConsumedEventLogEntity.of(topic, eventId.value()));
      return true;
    } catch (DataIntegrityViolationException e) {
      // 唯一索引冲突：说明其他实例已处理过此事件
      log.info("[Idempotency] DB 兜底拦截重复 topic={} eventId={}", topic, eventId);
      // 注意：此时 Redis 的占坑已经成功（first time），但 DB 拦截了——
      // 不需要 release Redis，因为最终结果就是"已被处理"，保留占坑是正确的
      return false;
    }
  }

  @Override
  public void release(String topic, EventId eventId) {
    // 仅释放 Redis 占坑；DB 记录保留作为审计日志（也作为永久幂等证据）
    redisGuard.release(topic, eventId);
  }
}
