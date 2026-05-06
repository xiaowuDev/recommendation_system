package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 消费幂等日志（JPA 实体）。
 *
 * <p>对应 SQL 表 {@code consumed_event_log}，作用是利用<b>唯一索引</b>
 * 在 DB 层强制幂等——这是 Redis 之外的"最后兜底"。
 *
 * <p>关键设计：
 * <ul>
 *   <li>唯一约束 {@code (topic, event_id)}：同一 topic 下同一业务 ID 只能写入一次</li>
 *   <li>普通索引 {@code consumed_at}：便于按时间清理历史数据</li>
 *   <li>不存储业务数据，只作幂等凭证 → 表轻量、易清理</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
    name = "consumed_event_log",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_topic_event_id",
        columnNames = {"topic", "event_id"}),
    indexes = @Index(name = "idx_consumed_at", columnList = "consumed_at"))
public class ConsumedEventLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "topic", length = 128, nullable = false)
  private String topic;

  @Column(name = "event_id", length = 64, nullable = false)
  private String eventId;

  @Column(name = "consumed_at", nullable = false)
  private Instant consumedAt;

  /** 工厂方法：构造一条新的幂等日志，consumedAt 自动取当前时间。 */
  public static ConsumedEventLogEntity of(String topic, String eventId) {
    return new ConsumedEventLogEntity(null, topic, eventId, Instant.now());
  }
}
