package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.kafka.publisher;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaowu.example.Kafka_Idempotent_Demo.application.port.DeadLetterPort;

/**
 * 死信端口的 Kafka 适配器。
 *
 * <p>失败消息会写入独立的 DLT topic（默认 {@code etl.order.events.DLT}），
 * 配合 Header 携带原始 topic 和失败原因，便于运维通过 Kafka UI / 自研工具排查。
 *
 * <p>截断策略：原始消息体最多保留 4096 字节，超长部分丢弃——
 * 防止超大消息撑爆 DLT topic 的存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "demo.kafka-idempotent", name = "enabled", havingValue = "true")
public class KafkaDeadLetterAdapter implements DeadLetterPort {

  private static final int MAX_PAYLOAD_BYTES = 4096;

  private final KafkaTemplate<String, String> kafkaTemplate;

  @Value("${demo.kafka-idempotent.topic.dlt:etl.order.events.DLT}")
  private String dltTopic;

  @Override
  public void sendToDeadLetter(String originalTopic, String rawPayload, String errorReason) {
    String truncated = truncate(rawPayload);

    ProducerRecord<String, String> record = new ProducerRecord<>(dltTopic, truncated);
    record.headers().add(new RecordHeader("x-original-topic", originalTopic.getBytes()));
    record.headers().add(new RecordHeader("x-error-reason", errorReason.getBytes()));

    kafkaTemplate.send(record);
    log.warn("[DLT] 已发送死信 originalTopic={} errorReason={} payloadLen={}",
        originalTopic, errorReason, truncated.length());
  }

  private String truncate(String payload) {
    if (payload == null) {
      return "";
    }
    byte[] bytes = payload.getBytes();
    if (bytes.length <= MAX_PAYLOAD_BYTES) {
      return payload;
    }
    return new String(bytes, 0, MAX_PAYLOAD_BYTES) + "...[truncated]";
  }
}
