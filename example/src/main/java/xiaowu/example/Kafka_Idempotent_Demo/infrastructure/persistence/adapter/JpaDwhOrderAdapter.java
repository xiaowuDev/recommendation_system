package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import xiaowu.example.Kafka_Idempotent_Demo.application.port.DwhOrderRepository;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.DwhOrder;
import xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.entity.DwhOrderEntity;
import xiaowu.example.Kafka_Idempotent_Demo.infrastructure.persistence.repository.DwhOrderJpaRepository;

/**
 * 数仓订单仓储的 JPA 适配器（六边形架构中的「驱动适配器」）。
 *
 * <p>把领域端口 {@link DwhOrderRepository} 翻译为 JPA 调用——
 * 应用层只感知端口，不感知 JPA / Hibernate / MyBatis 等具体技术。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "demo.kafka-idempotent", name = "enabled", havingValue = "true")
public class JpaDwhOrderAdapter implements DwhOrderRepository {

  private final DwhOrderJpaRepository jpaRepository;

  @Override
  public void upsert(DwhOrder order) {
    // JPA save 基于主键判断 INSERT / UPDATE，天然幂等
    jpaRepository.save(DwhOrderEntity.from(order));
  }
}
