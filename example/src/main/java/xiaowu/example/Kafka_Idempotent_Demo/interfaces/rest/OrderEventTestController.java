package xiaowu.example.Kafka_Idempotent_Demo.interfaces.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.EventId;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.OrderEvent;
import xiaowu.example.Kafka_Idempotent_Demo.domain.model.OrderStatus;

/**
 * 仅用于<b>本地手动验证</b>的测试接口。
 *
 * <p>提供两个动作：
 * <ul>
 *   <li>POST {@code /demo/order-event/send}：发一条新事件（每次新 eventId）</li>
 *   <li>POST {@code /demo/order-event/duplicate}：用相同 eventId 发 N 次（验证幂等）</li>
 * </ul>
 *
 * <p>线上环境务必关闭：通过 {@code demo.kafka-idempotent.enabled=false} 即可禁用全部 Demo 组件。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/demo/order-event")
@ConditionalOnProperty(prefix = "demo.kafka-idempotent", name = "enabled", havingValue = "true")
public class OrderEventTestController {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  /**
   * 发送一条新订单事件（每次都生成新的 eventId）。
   *
   * @param orderId 订单 ID（缺省自动生成）
   * @return 已发送事件的 eventId
   */
  @PostMapping("/send")
  public String sendOnce(@RequestParam(required = false) String orderId)
      throws JsonProcessingException {
    String finalOrderId = orderId != null ? orderId : "ORDER-" + System.currentTimeMillis();
    OrderEvent event = newEvent(UUID.randomUUID().toString(), finalOrderId);
    kafkaTemplate.send("etl.order.events", finalOrderId, objectMapper.writeValueAsString(event));
    return event.eventId().value();
  }

  /**
   * 用<b>相同 eventId</b> 重复发送 N 次，验证幂等屏障是否生效。
   *
   * @param times 发送次数（默认 5 次）
   * @return 此次复用的 eventId
   */
  @PostMapping("/duplicate")
  public String sendDuplicate(@RequestParam(defaultValue = "5") int times)
      throws JsonProcessingException {
    String orderId = "DUP-ORDER-" + System.currentTimeMillis();
    OrderEvent event = newEvent(UUID.randomUUID().toString(), orderId);
    String json = objectMapper.writeValueAsString(event);

    for (int i = 0; i < times; i++) {
      kafkaTemplate.send("etl.order.events", orderId, json);
    }
    return event.eventId().value();
  }

  private OrderEvent newEvent(String eventId, String orderId) {
    return new OrderEvent(
        new EventId(eventId),
        orderId,
        1001L,
        OrderStatus.PAID,
        new BigDecimal("99.50"),
        "USD",
        Instant.now());
  }
}
