package xiaowu.example.supplieretl.datasource.domain.model;

import java.util.Locale;

/**
 * MySQL 数据接入请求参数
 *
 * @param query   要执行的 SQL 查询语句（仅允许 SELECT）
 * @param maxRows 最大返回行数，默认 1000
 */
public record MysqlIngestionRequest(
    String query,
    Integer maxRows) implements IngestionRequest {

  private static final int DEFAULT_MAX_ROWS = 1000;
  private static final int MAX_ROWS_LIMIT = 50_000;

  public MysqlIngestionRequest {
    query = DataSourceConfigSupport.requireText(query, "query");
    maxRows = maxRows == null ? DEFAULT_MAX_ROWS : maxRows;
  }

  @Override
  public void validate() {
    if (maxRows <= 0 || maxRows > MAX_ROWS_LIMIT) {
      throw new IllegalArgumentException("maxRows must be between 1 and " + MAX_ROWS_LIMIT);
    }
    assertSelectOnly(query);
  }

  private static void assertSelectOnly(String sql) {
    String upper = sql.stripLeading().toUpperCase(Locale.ROOT);
    if (!upper.startsWith("SELECT")) {
      throw new IllegalArgumentException("Only SELECT statements are allowed");
    }
    String[] forbidden = {"INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CREATE", "GRANT", "REVOKE"};
    for (String keyword : forbidden) {
      if (upper.contains(keyword)) {
        throw new IllegalArgumentException("SQL contains forbidden keyword: " + keyword);
      }
    }
  }
}
