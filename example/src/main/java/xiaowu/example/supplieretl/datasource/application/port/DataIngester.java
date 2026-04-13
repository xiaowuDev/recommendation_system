package xiaowu.example.supplieretl.datasource.application.port;

import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;

/**
 * 数据接入执行器端口，每种数据源类型一个实现
 */
public interface DataIngester {

  DataSourceType supports();

  IngestionResult ingest(DataSourceConfig config, IngestionRequest request);
}
