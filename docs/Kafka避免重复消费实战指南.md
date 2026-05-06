# Kafka 避免重复消费实战指南

> 适用栈：Spring Boot 3.3.0 + Java 21 + Spring Kafka + Redis + MySQL
> 更新日期：2026-05-06
> 目标：提供可直接落地的代码模板和决策清单

---

## 0. 一句话结论

> **Kafka 默认只能保证「至少一次」，重复消费一定会发生。解决之道不是消灭重复，而是让重复消费的结果与单次消费完全相同——即「业务幂等」。**

---

## 1. 为什么会重复消费？根因图解

```
poll messages → 业务处理 → commit offset
                  ↑              ↑
            处理慢/超时        提交失败
            被 rebalance       网络抖动
                  ↓              ↓
              下次再消费 ←  offset 未推进
```

三种典型触发场景：

| 场景 | 触发原因 | 影响 |
|------|---------|------|
| 自动提交陷阱 | `enable.auto.commit=true`，处理与提交不同步 | 处理成功但崩溃 → 重复 |
| Rebalance 风暴 | Consumer 加入/退出，未提交 offset 被新 owner 接管 | 整批未提交消息重投 |
| 处理超时 | 单次 poll 处理时间 > `max.poll.interval.ms`（默认 5 分钟） | 被踢出消费组，重启后重投 |

---

## 2. 决策树：选择适合你场景的方案

```
是不是 Kafka → Kafka 流处理？
├─ 是 → 方案 C：事务 + read_committed
└─ 否
   └─ 是不是 DB → Kafka（本地事务和发消息要同时成功）？
      ├─ 是 → 方案 D：Outbox 模式
      └─ 否（最常见的 Kafka → DB / Kafka → 业务处理）
         └─ 方案 A（手动提交） + 方案 B（业务幂等）【强烈推荐】
```

| 方案 | 适用 | 复杂度 | 推荐度 |
|------|------|-------|-------|
| A. 手动提交 offset | 所有场景的基础 | ⭐⭐ | 必选 |
| B. 业务幂等（Redis SETNX / DB 唯一键） | 跨系统、99% 业务场景 | ⭐⭐ | 强烈推荐 |
| C. Kafka 事务 | Kafka → Kafka 流处理 | ⭐⭐⭐ | 仅流处理场景 |
| D. Outbox 模式 | 业务库与消息必须强一致 | ⭐⭐⭐⭐ | 仅高一致性场景 |

---

## 3. 完整实施方案（推荐组合：A + B）

### 3.1 依赖与配置

#### `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

#### `application.yml`（关键配置）

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: habit-analysis-group
      enable-auto-commit: false           # 关键 1：关闭自动提交
      auto-offset-reset: earliest         # 新消费组从头消费（首次启动）
      max-poll-records: 50                # 关键 2：单次 poll 数量，根据处理速度调整
      properties:
        max.poll.interval.ms: 600000      # 关键 3：超过此时间未 poll 会被踢出（10 分钟）
        session.timeout.ms: 30000
        isolation.level: read_committed   # 关键 4：只读已提交事务消息（开启事务时需要）
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties.spring.json.trusted.packages: "com.example.chatservice.*"
    listener:
      ack-mode: MANUAL_IMMEDIATE          # 关键 5：手动 ACK，业务处理完才提交
      concurrency: 3                      # 并发消费者数（建议 = 分区数）
      type: SINGLE                        # 单条处理，便于幂等
```

**配置项的工程含义：**

| 配置 | 默认值 | 推荐值 | 为什么 |
|------|-------|-------|-------|
| `enable.auto.commit` | true | **false** | 让"处理→提交"由业务控制，避免提前 commit |
| `max.poll.records` | 500 | **50** | 太大会拖长单批处理时间，触发 rebalance |
| `max.poll.interval.ms` | 300000 | **600000** | 业务处理慢时（如调用 AI），需要更大的窗口 |
| `ack-mode` | BATCH | **MANUAL_IMMEDIATE** | 单条手动 ACK，更细粒度的失败控制 |

---

### 3.2 核心代码：幂等消费者（拷贝即用模板）

#### 步骤 1：定义业务消息

```java
package com.example.chatservice.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class HabitAnalysisEvent {
    /**
     * 业务唯一 ID —— 幂等键的核心
     * 必须由生产者生成（如 UUID 或业务主键），不能用 Kafka offset
     */
    private String eventId;
    private Long userId;
    private String habitId;
    private String action;        // CREATED / UPDATED / COMPLETED
    private Instant occurredAt;
    private String payload;
}
```

> ⚠️ **关键**：`eventId` 必须由**生产者侧业务**生成，且在业务上唯一。常见做法：
> - `UUID.randomUUID().toString()`
> - `userId + ":" + habitId + ":" + occurredAt.toEpochMilli()`
> - 业务主键 `orderId`、`recordId` 等

#### 步骤 2：幂等服务（Redis SETNX）

```java
package com.example.chatservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotentService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "kafka:consumed:";
    private static final Duration TTL = Duration.ofDays(7);  // 根据业务回放窗口设置

    /**
     * 尝试占坑。
     * @return true = 第一次处理；false = 已处理过，应跳过
     */
    public boolean tryAcquire(String topic, String eventId) {
        String key = KEY_PREFIX + topic + ":" + eventId;
        Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", TTL);
        return Boolean.TRUE.equals(firstTime);
    }

    /**
     * 业务处理失败时释放占坑（让消息能被重投处理）。
     * 注意：只在确定可恢复的失败时释放，永久错误应进死信队列。
     */
    public void release(String topic, String eventId) {
        String key = KEY_PREFIX + topic + ":" + eventId;
        redis.delete(key);
    }
}
```

`★ Insight ─────────────────────────────────────`
- `setIfAbsent` (SET NX EX) 是 Redis 的原子操作，保证"占坑 + 设置过期"不可被中断
- TTL 必须 ≥ Kafka 的最大消息保留时间（默认 7 天），否则可能出现"幂等键已过期，但旧消息又被重新消费"
- 失败时是否 release 取决于失败类型：网络抖动 → release 重投；数据格式错误 → 不 release，进死信
`─────────────────────────────────────────────────`

#### 步骤 3：消费者实现

```java
package com.example.chatservice.consumer;

import com.example.chatservice.dto.HabitAnalysisEvent;
import com.example.chatservice.service.IdempotentService;
import com.example.chatservice.service.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class HabitAnalysisConsumer {

    private final IdempotentService idempotent;
    private final HabitService habitService;

    @KafkaListener(
        topics = "habit-analysis-events",
        groupId = "habit-analysis-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(rollbackFor = Exception.class)
    public void consume(ConsumerRecord<String, HabitAnalysisEvent> record,
                        Acknowledgment ack) {
        HabitAnalysisEvent event = record.value();
        String eventId = event.getEventId();
        String topic = record.topic();

        // ① 幂等检查
        if (!idempotent.tryAcquire(topic, eventId)) {
            log.info("[Kafka] 跳过重复消息 eventId={} topic={} offset={}",
                eventId, topic, record.offset());
            ack.acknowledge();   // 重复消息也要 ACK，否则会反复重投
            return;
        }

        try {
            // ② 业务处理（在同一个事务里）
            habitService.handleAnalysisEvent(event);

            // ③ 处理成功，提交 offset
            ack.acknowledge();
            log.info("[Kafka] 处理成功 eventId={} offset={}", eventId, record.offset());

        } catch (BusinessRetryableException e) {
            // 可恢复异常：释放幂等键，让 Kafka 重投（不 ACK）
            idempotent.release(topic, eventId);
            log.warn("[Kafka] 可重试异常 eventId={}, 不 ACK 等待重投", eventId, e);
            throw e;

        } catch (Exception e) {
            // 不可恢复异常：保留幂等键（不再重复处理），转死信队列
            log.error("[Kafka] 处理失败 eventId={}, 进入死信", eventId, e);
            ack.acknowledge();   // 主队列 ACK，避免一直重投
            sendToDeadLetter(record, e);
        }
    }

    private void sendToDeadLetter(ConsumerRecord<String, HabitAnalysisEvent> record,
                                  Exception e) {
        // 实现：发送到 DLT topic 或写入 DB 表，配合人工处理
    }
}
```

`★ Insight ─────────────────────────────────────`
- 三段式异常处理是关键：**重复 → 直接 ACK**，**可重试 → 不 ACK + release**，**不可恢复 → ACK + DLT**
- `@Transactional` 包裹的是**业务方法**（DB 操作），不是 Kafka 提交。Kafka offset 提交不参与 Spring 事务
- 一定要在 catch 里区分异常类型，否则要么死循环重试，要么悄悄丢消息
`─────────────────────────────────────────────────`

#### 步骤 4：双重幂等（DB 唯一索引兜底）

Redis 失效（雪崩、TTL 过期）时，DB 唯一键能兜底：

```sql
-- 消息处理记录表
CREATE TABLE consumed_message_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    consumer_group VARCHAR(64) NOT NULL,
    consumed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_topic_event_group (topic, event_id, consumer_group)
);
```

```java
@Transactional
public void handleAnalysisEvent(HabitAnalysisEvent event) {
    try {
        // 先插入幂等表，靠唯一索引拦截重复
        consumedLogRepo.save(new ConsumedMessageLog(
            "habit-analysis-events", event.getEventId(), "habit-analysis-group"
        ));
    } catch (DataIntegrityViolationException e) {
        log.info("DB 幂等拦截 eventId={}", event.getEventId());
        return;
    }

    // 业务处理
    habitRepository.save(toEntity(event));
}
```

> ✅ **双重保险**：Redis SETNX 拦截 99.9% 的重复（高性能），DB 唯一索引兜底剩下 0.1%（强一致）。两者**同一个事务**内执行。

---

### 3.3 进阶方案：Kafka 事务（仅流处理场景）

只在 **「消费 → 转换 → 再生产到 Kafka」** 时需要：

```java
@Configuration
public class KafkaTransactionConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "habit-tx-");  // 必填
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);        // 必填
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(props);
        pf.setTransactionIdPrefix("habit-tx-");
        return pf;
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> pf) {
        return new KafkaTransactionManager<>(pf);
    }
}
```

```java
@KafkaListener(topics = "input-topic")
@Transactional("kafkaTransactionManager")  // 用 Kafka 事务管理器
public void streamProcess(ConsumerRecord<String, String> record, Acknowledgment ack) {
    String result = transform(record.value());
    kafkaTemplate.send("output-topic", result);
    // Spring 会自动调用 sendOffsetsToTransaction，offset 与输出消息原子提交
}
```

> ⚠️ Consumer 端必须设置 `isolation.level=read_committed`，否则会读到未提交的事务消息。

---

## 4. 验证 Checklist（实施完代码后逐项打勾）

### 配置层

- [ ] `enable.auto.commit=false`
- [ ] `ack-mode=MANUAL_IMMEDIATE` 或 `MANUAL`
- [ ] `max.poll.records` ≤ 50（根据业务调整）
- [ ] `max.poll.interval.ms` > 单批最长处理时间 × 2
- [ ] `auto.offset.reset` 明确（earliest 或 latest，避免 none）

### 代码层

- [ ] 业务消息有稳定的 `eventId`（不是 offset）
- [ ] 幂等检查在业务逻辑**之前**
- [ ] 重复消息也调用 `ack.acknowledge()`
- [ ] 异常分类：可重试 / 不可恢复 / 重复
- [ ] 死信队列（DLT）兜底
- [ ] DB 唯一索引兜底（双重幂等）
- [ ] Redis 幂等 TTL ≥ Kafka 消息保留期

### 测试层

- [ ] 单元测试：模拟同一 eventId 调用两次，验证只处理一次
- [ ] 集成测试：手动 kill 消费者中途，验证重启后不重复处理
- [ ] 压测：并发消费同一 eventId（多实例），验证只有一个成功

---

## 5. 常见坑汇总

| 坑 | 现象 | 解法 |
|----|------|------|
| 用 Kafka offset 当幂等键 | Rebalance 后 offset 变化，幂等失效 | 改用业务 eventId |
| 幂等 TTL 太短 | 旧消息被重消费时已过期 → 重复处理 | TTL ≥ 消息保留时间 |
| 异常时全部重投 | 数据格式错误也无限重试 | 区分异常类型 + DLT |
| 自动提交 + 手动 ACK 混用 | 看起来手动 ACK 实则没生效 | `enable.auto.commit=false` |
| 单批超大批量插入 | 一条失败整批重投 | 缩小 `max.poll.records` |
| 处理调用外部 API | 单次处理超 5 分钟，被踢出 | 加大 `max.poll.interval.ms`，或改异步 |
| 没有死信队列 | 错误消息阻塞整个分区 | 配 `DefaultErrorHandler` + DLT |

---

## 6. 配 DLT（死信队列）的最简模板

```java
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        // 重试 3 次，间隔 1s（指数退避），失败进 DLT
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
```

---

## 7. 你项目中的落地建议

针对**习惯分析系统**，建议按以下优先级实施：

1. **第一步**（必做）：配置层 5 项 + 业务幂等服务（Redis SETNX）
2. **第二步**（推荐）：DB 唯一索引兜底
3. **第三步**（可选）：DLT + 监控告警

**典型场景对应**：

| 业务 | 推荐方案 |
|------|---------|
| 用户上报习惯完成事件 → 写库 | A + B（Redis 幂等） |
| AI 分析结果回写 → 推送到前端 | A + B + DLT |
| 习惯数据 → 推荐系统（双 Kafka） | A + B + C（事务） |
| 计划生成（AI 调用慢） | A + B + 加大 `max.poll.interval.ms` |

---

## 8. 配套示例代码（DDD 架构 · ETL 场景）

本文档配套的可运行 Demo 已落地在仓库中：

> 📁 `example/src/main/java/xiaowu/example/Kafka_Idempotent_Demo/`
> 📖 [Demo 内 README（含启用步骤、代码导读、决策树）](../example/src/main/java/xiaowu/example/Kafka_Idempotent_Demo/README.md)
> 🗄️ SQL 表结构：`example/src/main/resources/sql/kafka_idempotent_demo.sql`

### 8.1 Demo 业务场景

模拟「上游订单系统 → Kafka → 本服务 ETL → 数仓表 `dwh_orders`」流程，演示订单事件被重复消费时如何保持数仓数据一致。

### 8.2 Demo 架构分层（六边形 / DDD）

| 层 | 关键文件 | 职责 |
|----|---------|------|
| Domain | `EventId`、`OrderEvent`、`PermanentEtlException`、`TransientEtlException` | 不依赖任何框架的纯领域模型 |
| Application | `IdempotencyGuard`（端口）、`OrderEtlApplicationService` | 编排「幂等检查 → 转换 → 持久化」三阶段 |
| Infrastructure | `RedisIdempotencyGuard`、`DualLayerIdempotencyGuard`、`OrderEventConsumer`、`KafkaDeadLetterAdapter` | Redis / Kafka / JPA 适配器实现 |
| Interfaces | `OrderEventTestController` | 仅供本地手动验证幂等效果 |

### 8.3 Demo 实现的三道防线

| 层级 | 实现类 | 拦截范围 |
|------|-------|---------|
| 一级（O(1)） | `RedisIdempotencyGuard` | 99.9% 常规重复 |
| 二级（强一致） | `DualLayerIdempotencyGuard`（DB 唯一索引） | Redis 失效 / TTL 过期 |
| 三级（最终） | `JpaDwhOrderAdapter`（主键 upsert） | 极端兜底，业务最终一致 |

### 8.4 一键启用

```yaml
demo:
  kafka-idempotent:
    enabled: true
    dual-layer: true
```

启用后用如下命令验证：

```bash
# 同一 eventId 重发 5 次 → 只处理 1 次
curl -X POST "http://localhost:8080/demo/order-event/duplicate?times=5"
```

### 8.5 与本文档章节的对应关系

| 文档章节 | Demo 中的体现 |
|---------|--------------|
| §3.1 配置 | `IdempotentKafkaConfig.java` 的 `idempotentDemoConsumerFactory` |
| §3.2 步骤 1（业务消息） | `OrderEvent.java` + `EventId.java` |
| §3.2 步骤 2（幂等服务） | `RedisIdempotencyGuard.java` |
| §3.2 步骤 3（消费者实现） | `OrderEventConsumer.java` |
| §3.2 步骤 4（双重幂等） | `DualLayerIdempotencyGuard.java` |
| §6（DLT 配置） | `KafkaDeadLetterAdapter.java` + `IdempotentKafkaConfig` 中的 `DefaultErrorHandler` |

---

## 9. 参考文档

- Apache Kafka 4.2 Javadoc：[KafkaConsumer Manual Offset Control](https://javadoc.io/doc/org.apache.kafka/kafka-clients/4.2.0/org/apache/kafka/clients/consumer/KafkaConsumer.html)
- Spring Kafka 文档：`org.springframework.kafka.listener.ContainerProperties.AckMode`
- 项目内文档：`docs/技术文档.md`、`docs/3张表的各自保留周期和清理任务.md`
- 配套 Demo：[`example/src/main/java/xiaowu/example/Kafka_Idempotent_Demo/`](../example/src/main/java/xiaowu/example/Kafka_Idempotent_Demo/README.md)

---

**维护**：实施过程中如发现新坑或新模式，请补充到第 5 节
**作者**：xiaowu（基于 Kafka 4.2 文档整理）
**更新**：2026-05-06 - 新增 §8 配套 DDD Demo 索引
