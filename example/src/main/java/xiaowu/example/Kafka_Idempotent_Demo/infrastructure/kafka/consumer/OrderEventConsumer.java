package xiaowu.example.Kafka_Idempotent_Demo.infrastructure.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaowu.example.Kafka_Idempotent_Demo.application.port.DeadLetterPort;
import xiaowu.example.Kafka_Idempotent_Demo.application.service.OrderEtlApplicationService;
import xiaowu.example.Kafka_Idempotent_Demo.application.service.OrderEtlApplicationService.Outcome;
import xiaowu.example.Kafka_Idempotent_Demo.domain.exception.PermanentEtlException;
import xiaowu.example.Kafka_Idempotent_Demo.domain.exception.TransientEtlException;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.OrderEvent;

/**
 * 订单事件 Kafka 消费者（六边形架构中的「输入适配器」）。
 *
 * <p>这是整个 Demo 的<b>入口</b>，但只承担「翻译 + 委派」的薄职责：
 * <ol>
 *   <li>反序列化：JSON → {@link OrderEvent} 领域对象</li>
 *   <li>委派：调用 {@link OrderEtlApplicationService#process} 处理业务</li>
 *   <li>异常分类：根据领域异常类型决定 ACK / 重投 / DLT</li>
 * </ol>
 *
 * <p>关键配置（见 application.yml）：
 * <ul>
 *   <li>{@code enable-auto-commit=false}：关闭自动提交</li>
 *   <li>{@code ack-mode=MANUAL_IMMEDIATE}：手动 ACK，业务成功后立即提交 offset</li>
 *   <li>{@code max-poll-records=50}：单次拉取数量适中，避免单批超时</li>
 * </ul>
 *
 * <p><b>异常处理三段式：</b>
 * <pre>
 *   try {
 *     applicationService.process(...);
 *     ack.acknowledge();              // 成功 → ACK
 *   } catch (PermanentEtlException) {
 *     deadLetter.send(...);
 *     ack.acknowledge();              // 永久失败 → 转 DLT + ACK
 *   } catch (TransientEtlException) {
 *     // 不 ACK + 抛出 → 框架重投
 *   }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "demo.kafka-idempotent", name = "enabled", havingValue = "true")
public class OrderEventConsumer {

  private final OrderEtlApplicationService applicationService;
  private final DeadLetterPort deadLetterPort;
  private final ObjectMapper objectMapper;

  @KafkaListener(
      topics = "${demo.kafka-idempotent.topic.input:etl.order.events}",
      groupId = "${demo.kafka-idempotent.consumer.group-id:order-etl-group}",
      containerFactory = "idempotentDemoListenerContainerFactory")
  public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
    String topic = record.topic();
    String payload = record.value();

    OrderEvent event;
    try {
      // ① 反序列化：失败说明上游 Schema 错误 → 永久失败（无需重试）
      event = objectMapper.readValue(payload, OrderEvent.class);
    } catch (Exception e) {
      log.error("[OrderConsumer] 反序列化失败，转 DLT topic={} offset={}",
          topic, record.offset(), e);
      deadLetterPort.sendToDeadLetter(topic, payload,
          "DESERIALIZE_FAIL: " + e.getClass().getSimpleName());
      ack.acknowledge(); // 反序列化失败也要 ACK，否则毒消息阻塞分区
      return;
    }

    try {
      // ② 委派给应用服务处理
      Outcome outcome = applicationService.process(event, topic);

      // ③ 处理成功（含「重复跳过」）→ ACK
      ack.acknowledge();
      log.info("[OrderConsumer] {} eventId={} orderId={} offset={}",
          outcome, event.eventId(), event.orderId(), record.offset());

    } catch (PermanentEtlException e) {
      // 不可恢复：转死信 + ACK，避免毒消息阻塞分区
      log.error("[OrderConsumer] 永久失败 → DLT eventId={} reason={}",
          event.eventId(), e.getMessage(), e);
      deadLetterPort.sendToDeadLetter(topic, payload,
          "PERMANENT: " + e.getMessage());
      ack.acknowledge();

    } catch (TransientEtlException e) {
      // 可恢复：不 ACK，抛出让 DefaultErrorHandler 按退避策略重投
      log.warn("[OrderConsumer] 瞬时失败，等待重投 eventId={} reason={}",
          event.eventId(), e.getMessage());
      throw e;

    } catch (Exception e) {
      // 兜底：未明确分类的异常 → 默认归为瞬时，让框架重试
      // 重试达到上限后由 DefaultErrorHandler 自动转 DLT（见 KafkaConfig）
      log.error("[OrderConsumer] 未分类异常，按瞬时处理 eventId={}",
          event.eventId(), e);
      throw new TransientEtlException("未分类异常: " + e.getMessage(), e);
    }
  }
}
