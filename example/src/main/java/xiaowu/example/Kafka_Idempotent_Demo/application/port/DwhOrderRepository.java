package xiaowu.example.Kafka_Idempotent_Demo.application.port;

import xiaowu.example.Kafka_Idempotent_Demo.domain.model.DwhOrder;

/**
 * 数仓订单仓储端口。
 *
 * <p>应用服务通过此接口将 ETL 转换后的 {@link DwhOrder}
 * 写入数据仓库（MySQL / 实际项目可换成 ClickHouse、Doris 等）。
 *
 * <p>实现必须保证 {@link #upsert} 是<b>幂等</b>的——
 * 即对同一 {@code orderId} 多次调用，最终状态等价于一次调用。
 * 这是与 Redis 幂等屏障互补的<b>第二道防线</b>。
 */
public interface DwhOrderRepository {

  /**
   * 写入或更新一条数仓订单记录。
   *
   * <p>实现侧应使用 {@code INSERT ... ON DUPLICATE KEY UPDATE}
   * 或 JPA 的 {@code save()}（基于主键覆盖）来保证幂等。
   *
   * @param order 待写入的数仓记录
   */
  void upsert(DwhOrder order);
}
