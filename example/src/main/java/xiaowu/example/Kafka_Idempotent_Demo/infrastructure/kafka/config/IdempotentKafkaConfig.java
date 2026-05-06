package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.kafka.config;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka 幂等 Demo 的全部配置（Topic + Consumer Factory + 错误处理器）。
 *
 * <p>设计要点：
 * <ol>
 *   <li>使用<b>独立的 ConsumerFactory</b>（{@code idempotentDemoConsumerFactory}），
 *       不与项目其他模块共享，避免互相影响</li>
 *   <li>{@code ENABLE_AUTO_COMMIT_CONFIG=false} + {@code AckMode.MANUAL_IMMEDIATE}
 *       是手动 ACK 的双保险</li>
 *   <li>{@link DefaultErrorHandler} 配指数退避重试，重试到顶后自动转 DLT</li>
 * </ol>
 *
 * <p>整个配置类由 {@code demo.kafka-idempotent.enabled=true} 驱动启用，
 * 不影响项目其他 Kafka 配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(prefix = "demo.kafka-idempotent", name = "enabled", havingValue = "true")
public class IdempotentKafkaConfig {

  /** 默认输入 topic。 */
  public static final String DEFAULT_INPUT_TOPIC = "etl.order.events";

  /** 默认死信 topic。 */
  public static final String DEFAULT_DLT_TOPIC = "etl.order.events.DLT";

  // ─── Topic 声明 ─────────────────────────────────────────────────────────

  @Bean
  NewTopic orderEventsTopic() {
    return TopicBuilder.name(DEFAULT_INPUT_TOPIC)
        .partitions(3)        // 与 listener.concurrency 匹配，每分区一个消费者
        .replicas(1)
        .build();
  }

  @Bean
  NewTopic orderEventsDltTopic() {
    return TopicBuilder.name(DEFAULT_DLT_TOPIC)
        .partitions(3)
        .replicas(1)
        .build();
  }

  // ─── Consumer 工厂（独立配置） ──────────────────────────────────────────

  /**
   * 独立的 ConsumerFactory，确保此 Demo 的消费者配置与全局配置隔离。
   *
   * <p>关键参数：
   * <ul>
   *   <li>{@code ENABLE_AUTO_COMMIT_CONFIG=false}：禁用自动提交</li>
   *   <li>{@code MAX_POLL_RECORDS_CONFIG=50}：单次 poll 数量适中</li>
   *   <li>{@code MAX_POLL_INTERVAL_MS_CONFIG=600000}：10 分钟，给业务处理足够缓冲</li>
   *   <li>{@code AUTO_OFFSET_RESET_CONFIG=earliest}：新消费组从头消费（配合幂等无副作用）</li>
   * </ul>
   */
  @Bean
  ConsumerFactory<String, String> idempotentDemoConsumerFactory(
      org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600_000);
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30_000);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * 监听器容器工厂：开启手动 ACK + 配置错误处理器。
   *
   * <p>{@code AckMode.MANUAL_IMMEDIATE}：每条消息处理完立即提交 offset，
   * 比 {@code MANUAL}（按批提交）更细粒度，但调用次数更多——
   * 对低 QPS 业务（如订单 ETL）完全可接受。
   */
  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> idempotentDemoListenerContainerFactory(
      ConsumerFactory<String, String> idempotentDemoConsumerFactory,
      DefaultErrorHandler idempotentDemoErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(idempotentDemoConsumerFactory);
    factory.setConcurrency(3); // 与分区数对齐
    factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
    factory.setCommonErrorHandler(idempotentDemoErrorHandler);
    return factory;
  }

  /**
   * 错误处理器：指数退避重试 + 终极转 DLT。
   *
   * <p>退避策略：1s → 2s → 4s → 8s（最多 4 次），仍失败则由
   * {@link DeadLetterPublishingRecoverer} 自动写入
   * {@code etl.order.events-dlt} 主题。
   *
   * <p>注意：消费者代码里捕获 {@link xiaowu.example.Kafka_Idempotent_Demo.domain.exception.PermanentEtlException}
   * 后<b>主动</b>调用 DLT 端口转发，不会走到这个错误处理器；只有未捕获的异常或
   * {@link xiaowu.example.Kafka_Idempotent_Demo.domain.exception.TransientEtlException}
   * 会落入这里。
   */
  @Bean
  DefaultErrorHandler idempotentDemoErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + "-dlt", record.partition()));

    ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
    backOff.setMaxInterval(8_000L);
    backOff.setMaxElapsedTime(30_000L); // 整体重试不超过 30s

    return new DefaultErrorHandler(recoverer, backOff);
  }

  // ─── 工具 Bean ────────────────────────────────────────────────────────

  /** 应用服务依赖的时钟，便于单元测试注入固定时间。 */
  @Bean
  @ConditionalOnProperty(prefix = "demo.kafka-idempotent", name = "enabled", havingValue = "true")
  Clock idempotentDemoClock() {
    return Clock.systemUTC();
  }
}
