package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.entity.ConsumedEventLogEntity;

/**
 * 消费幂等日志的 Spring Data JPA Repository。
 *
 * <p>仅暴露 {@code save} 即可——双层幂等的 DB 侧只需要插入并捕获
 * {@link org.springframework.dao.DataIntegrityViolationException}，
 * 不需要 select 查询。
 */
@Repository
public interface ConsumedEventLogJpaRepository
    extends JpaRepository<ConsumedEventLogEntity, Long> {
}
