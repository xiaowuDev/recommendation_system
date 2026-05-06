-- =====================================================================
-- Kafka 幂等消费 Demo 所需表
-- 配套代码：example/src/main/java/xiaowu/example/Kafka_Idempotent_Demo
-- =====================================================================

-- ── 1. 消费幂等日志（双层幂等的 DB 兜底层）────────────────────────────
DROP TABLE IF EXISTS consumed_event_log;
CREATE TABLE consumed_event_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    topic        VARCHAR(128) NOT NULL                COMMENT '消息所在 topic',
    event_id     VARCHAR(64)  NOT NULL                COMMENT '业务唯一 ID（生产者侧生成）',
    consumed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_event_id (topic, event_id),
    KEY idx_consumed_at (consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kafka 消费幂等日志（DB 兜底）';

-- ── 2. 数仓订单表（ETL 落地目标）──────────────────────────────────────
DROP TABLE IF EXISTS dwh_orders;
CREATE TABLE dwh_orders (
    order_id         VARCHAR(64)    NOT NULL COMMENT '订单业务 ID（数仓主键）',
    user_id          BIGINT         NOT NULL COMMENT '下单用户 ID',
    status           VARCHAR(16)    NOT NULL COMMENT '订单状态：CREATED/PAID/SHIPPED/COMPLETED/CANCELLED',
    amount_cny       DECIMAL(18, 2) NOT NULL COMMENT '统一换算后金额（CNY）',
    original_amount  DECIMAL(18, 2) NOT NULL COMMENT '原始金额',
    original_ccy     VARCHAR(8)     NOT NULL COMMENT '原始币种',
    occurred_at      TIMESTAMP      NOT NULL COMMENT '事件发生时间（源系统）',
    etl_at           TIMESTAMP      NOT NULL COMMENT 'ETL 处理时间',
    PRIMARY KEY (order_id),
    KEY idx_user_id (user_id),
    KEY idx_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数仓订单表（ETL 目标表）';

-- =====================================================================
-- 维护建议（生产环境）：
--   1. consumed_event_log 按 consumed_at 定期清理（建议保留 30 天）
--      DELETE FROM consumed_event_log WHERE consumed_at < NOW() - INTERVAL 30 DAY;
--   2. dwh_orders 按 occurred_at 做分区（PARTITION BY RANGE），便于历史归档
-- =====================================================================
