# Kafka 幂等消费 Demo（DDD 架构 · ETL 场景）

> 配套文档：[../../../../../../docs/Kafka避免重复消费实战指南.md](../../../../../../docs/Kafka避免重复消费实战指南.md)

## 0. 这个 Demo 解决什么问题？

**业务场景**：上游订单系统通过 Kafka 推送订单事件 → 本服务消费 → ETL 转换（汇率统一为 CNY）→ 写入数据仓库表 `dwh_orders`。

**核心痛点**：Kafka 默认是 **at-least-once**，同一条订单事件可能被消费多次，造成数仓数据重复或统计错误。

**本 Demo 的解法**：**Redis SETNX**（一级，O(1) 高性能）+ **MySQL 唯一索引**（二级，强一致兜底） + **JPA upsert**（三级，主键覆盖）的三重防御，确保同一 `eventId` 无论被消费几次，最终数仓状态完全一致。

---

## 1. DDD 架构总览

```
Kafka_Idempotent_Demo/
├── domain/                          # 领域层（零框架依赖）
│   ├── model/
│   │   ├── EventId.java             # 值对象：业务唯一幂等键
│   │   ├── OrderEvent.java          # 聚合根：订单事件
│   │   ├── OrderStatus.java         # 枚举：订单状态
│   │   └── DwhOrder.java            # 实体：数仓订单
│   └── exception/
│       ├── PermanentEtlException.java  # 永久失败 → DLT
│       └── TransientEtlException.java  # 瞬时失败 → 重投
│
├── application/                     # 应用层（编排业务流程）
│   ├── port/                        # 端口（接口）
│   │   ├── IdempotencyGuard.java    # 幂等屏障端口
│   │   ├── DwhOrderRepository.java  # 数仓仓储端口
│   │   └── DeadLetterPort.java      # 死信端口
│   └── service/
│       └── OrderEtlApplicationService.java  # 三阶段编排
│
├── infrastructure/                  # 基础设施层（适配器实现）
│   ├── idempotency/
│   │   ├── RedisIdempotencyGuard.java        # 一级：Redis SETNX
│   │   └── DualLayerIdempotencyGuard.java    # 双层：Redis + DB
│   ├── persistence/
│   │   ├── entity/                  # JPA 实体
│   │   ├── repository/              # JpaRepository
│   │   └── adapter/                 # 端口实现
│   └── kafka/
│       ├── config/IdempotentKafkaConfig.java   # 监听器/Topic/错误处理器
│       ├── consumer/OrderEventConsumer.java    # 消费者（薄壳）
│       └── publisher/KafkaDeadLetterAdapter.java
│
└── interfaces/                      # 接口层（HTTP 触发器）
    └── rest/OrderEventTestController.java     # 仅供本地手动验证
```

---

## 2. 启用 Demo（4 步）

### 2.1 在 `application.yml` 加入开关

```yaml
demo:
  kafka-idempotent:
    enabled: true                    # 开关：默认关闭，避免影响其他模块
    dual-layer: true                 # 启用双层幂等（Redis + DB）
    ttl-days: 7                      # Redis 幂等键过期时间（≥ Kafka 消息保留期）
    topic:
      input: etl.order.events        # 输入主题
      dlt: etl.order.events.DLT      # 死信主题
    consumer:
      group-id: order-etl-group

spring:
  kafka:
    bootstrap-servers: localhost:9092
    # 其他全局参数继承项目原有配置
```

### 2.2 创建数据库表

```bash
mysql -u root -p your_db < example/src/main/resources/sql/kafka_idempotent_demo.sql
```

### 2.3 启动应用

```bash
cd example
./mvnw spring-boot:run
```

启动日志中应该看到：
```
[idempotentDemoListenerContainerFactory] Started listening on topic etl.order.events
```

### 2.4 手动验证幂等

```bash
# ① 发一条正常事件（应被处理）
curl -X POST http://localhost:8080/demo/order-event/send

# ② 用相同 eventId 重复发送 5 次（应只处理 1 次）
curl -X POST "http://localhost:8080/demo/order-event/duplicate?times=5"
```

观察日志：
```
[OrderConsumer] PROCESSED eventId=xxx orderId=DUP-ORDER-1714000000000 offset=0
[OrderConsumer] DUPLICATE eventId=xxx orderId=DUP-ORDER-1714000000000 offset=1
[OrderConsumer] DUPLICATE eventId=xxx orderId=DUP-ORDER-1714000000000 offset=2
[OrderConsumer] DUPLICATE eventId=xxx orderId=DUP-ORDER-1714000000000 offset=3
[OrderConsumer] DUPLICATE eventId=xxx orderId=DUP-ORDER-1714000000000 offset=4
```

✅ 5 条消息消费成功，但只有 1 条 PROCESSED；数仓表 `dwh_orders` 中只有 1 条记录。

---

## 3. 核心代码导读（按学习顺序）

| # | 文件 | 关注点 |
|---|------|-------|
| 1 | `domain/model/EventId.java` | 为什么用值对象包装 String，而不是裸 String |
| 2 | `domain/model/OrderEvent.java` | record 紧凑构造器做不变量校验 |
| 3 | `domain/exception/*` | Transient vs Permanent 的工程意义 |
| 4 | `application/port/IdempotencyGuard.java` | 端口抽象 + 实现可替换 |
| 5 | `application/service/OrderEtlApplicationService.java` | 三阶段编排 + 异常分类 |
| 6 | `infrastructure/idempotency/RedisIdempotencyGuard.java` | SETNX 原子操作 |
| 7 | `infrastructure/idempotency/DualLayerIdempotencyGuard.java` | 双层兜底设计 |
| 8 | `infrastructure/kafka/config/IdempotentKafkaConfig.java` | 配置项与默认值的工程含义 |
| 9 | `infrastructure/kafka/consumer/OrderEventConsumer.java` | 三段式异常处理 |

---

## 4. 异常处理决策树

```
消息进入 onMessage
    │
    ├─ JSON 反序列化失败？
    │     └─ YES → 转 DLT + ACK（不重试，毒消息）
    │
    └─ NO → 调用 applicationService.process()
              │
              ├─ 幂等屏障返回 false（重复）→ ACK，记 DUPLICATE 日志
              │
              ├─ 业务处理成功 → ACK，记 PROCESSED 日志
              │
              ├─ PermanentEtlException → 转 DLT + ACK（保留幂等键）
              │
              └─ TransientEtlException → 不 ACK + 释放幂等键
                                          → DefaultErrorHandler 退避重试
                                          → 4 次仍失败 → 自动转 DLT
```

---

## 5. 幂等三道防线

| 层级 | 实现 | 性能 | 故障应对 |
|------|------|------|---------|
| 一级 | Redis SETNX | ≈ 1ms | Redis 故障时降级到二级 |
| 二级 | MySQL 唯一索引 | ≈ 5ms | 强一致，作为最终凭证 |
| 三级 | JPA save() 主键 upsert | ≈ 5ms | 即使前两层失效，主键覆盖保证业务一致 |

> **设计哲学**：性能优先时让 Redis 拦下 99.9% 的重复，DB 唯一索引和主键 upsert 兜底剩下 0.x%——三层各司其职，组合更稳。

---

## 6. 与生产环境的差异（落地前补齐）

- [ ] 汇率表替换为配置中心 / 数据库实时查询
- [ ] DLT 消息接入告警通道（钉钉 / 飞书 / Slack）
- [ ] `consumed_event_log` 接入定时清理任务（保留 30 天即可）
- [ ] 监控指标：消费速率、幂等命中率、DLT 速率
- [ ] 压测：多实例并发消费同一 eventId 验证 DB 兜底真的生效

---

## 7. 不适用此方案的场景

- **Kafka → Kafka 流处理**：用事务（`@Transactional("kafkaTransactionManager")` + `read_committed`）
- **DB → Kafka 强一致**：用 Outbox 模式（业务事务里写 outbox 表 + 单独 relay）
- **去重要求 100%**：幂等三道防线在极端情况（Redis + DB 同时故障）下仍可能失效，此时需要业务层补偿对账

---

**作者**：xiaowu
**更新**：2026-05-06
**关联文档**：[Kafka 避免重复消费实战指南](../../../../../../docs/Kafka避免重复消费实战指南.md)
