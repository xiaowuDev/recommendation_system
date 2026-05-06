package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.entity.DwhOrderEntity;

/**
 * 数仓订单表的 Spring Data JPA Repository。
 *
 * <p>{@code save()} 在 JPA 语义中：主键存在 → UPDATE，不存在 → INSERT，
 * 天然 upsert，作为幂等的<b>第三层兜底</b>。
 */
@Repository
public interface DwhOrderJpaRepository
    extends JpaRepository<DwhOrderEntity, String> {
}
