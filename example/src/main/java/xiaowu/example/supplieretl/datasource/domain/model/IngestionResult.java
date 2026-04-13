package xiaowu.example.supplieretl.datasource.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 数据接入执行结果
 *
 * @param totalRecords 读取到的总记录数
 * @param columns      列名列表
 * @param sampleRows   前 N 行采样数据
 * @param metadata     额外元数据（如 Kafka offset、MySQL 查询耗时等）
 */
public record IngestionResult(
    int totalRecords,
    List<String> columns,
    List<Map<String, Object>> sampleRows,
    Map<String, Object> metadata) {

  private static final int DEFAULT_SAMPLE_SIZE = 20;

  public IngestionResult {
    columns = columns == null ? List.of() : List.copyOf(columns);
    sampleRows = sampleRows == null ? List.of() : List.copyOf(sampleRows);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static IngestionResult of(
      int totalRecords,
      List<String> columns,
      List<Map<String, Object>> allRows,
      Map<String, Object> metadata) {
    List<Map<String, Object>> sample = allRows.size() <= DEFAULT_SAMPLE_SIZE
        ? allRows
        : allRows.subList(0, DEFAULT_SAMPLE_SIZE);
    return new IngestionResult(totalRecords, columns, sample, metadata);
  }
}
