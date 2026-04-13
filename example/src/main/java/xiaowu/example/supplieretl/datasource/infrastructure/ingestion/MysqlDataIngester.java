package xiaowu.example.supplieretl.datasource.infrastructure.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlDataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;

@Component
public class MysqlDataIngester implements DataIngester {

  private static final int QUERY_TIMEOUT_SECONDS = 30;

  @Override
  public DataSourceType supports() {
    return DataSourceType.MYSQL;
  }

  @Override
  public IngestionResult ingest(DataSourceConfig config, IngestionRequest request) {
    MysqlDataSourceConfig mysqlConfig = (MysqlDataSourceConfig) config;
    MysqlIngestionRequest mysqlRequest = (MysqlIngestionRequest) request;

    try {
      Class.forName(mysqlConfig.driverClassName());
    } catch (ClassNotFoundException ex) {
      throw new IllegalStateException("MySQL driver not found: " + mysqlConfig.driverClassName(), ex);
    }

    try (Connection conn = DriverManager.getConnection(
        mysqlConfig.jdbcUrl(),
        mysqlConfig.username(),
        mysqlConfig.password() == null ? "" : mysqlConfig.password())) {

      conn.setReadOnly(true);
      try (Statement stmt = conn.createStatement()) {
        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        stmt.setMaxRows(mysqlRequest.maxRows());

        long startTime = System.currentTimeMillis();
        try (ResultSet rs = stmt.executeQuery(mysqlRequest.query())) {
          long queryTimeMs = System.currentTimeMillis() - startTime;

          ResultSetMetaData meta = rs.getMetaData();
          int columnCount = meta.getColumnCount();
          List<String> columns = new ArrayList<>(columnCount);
          for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
          }

          List<Map<String, Object>> rows = new ArrayList<>();
          while (rs.next() && rows.size() < mysqlRequest.maxRows()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
              row.put(columns.get(i - 1), rs.getObject(i));
            }
            rows.add(row);
          }

          Map<String, Object> metadata = new HashMap<>();
          metadata.put("jdbcUrl", mysqlConfig.jdbcUrl());
          metadata.put("query", mysqlRequest.query());
          metadata.put("queryTimeMs", queryTimeMs);
          metadata.put("columnCount", columnCount);

          return IngestionResult.of(rows.size(), columns, rows, Collections.unmodifiableMap(metadata));
        }
      }
    } catch (Exception ex) {
      throw new IllegalStateException("MySQL ingestion failed: " + ex.getMessage(), ex);
    }
  }
}
