package xiaowu.example.supplieretl.datasource.domain.model;

/**
 * 数据接入请求参数，按数据源类型分四种实现
 */
public sealed interface IngestionRequest
    permits KafkaIngestionRequest, MysqlIngestionRequest, RedisIngestionRequest, ExcelIngestionRequest {

  void validate();
}
