package xiaowu.example.supplieretl.datasource.domain.model;

/**
 * Excel 数据接入请求参数
 *
 * @param sheetName      工作表名称，null 则使用第一个 sheet
 * @param headerRowIndex 表头行索引，默认 0
 * @param maxRows        最大读取行数，默认 1000
 */
public record ExcelIngestionRequest(
    String sheetName,
    Integer headerRowIndex,
    Integer maxRows) implements IngestionRequest {

  private static final int DEFAULT_HEADER_ROW_INDEX = 0;
  private static final int DEFAULT_MAX_ROWS = 1000;
  private static final int MAX_ROWS_LIMIT = 50_000;

  public ExcelIngestionRequest {
    sheetName = DataSourceConfigSupport.normalizeNullableText(sheetName);
    headerRowIndex = headerRowIndex == null ? DEFAULT_HEADER_ROW_INDEX : headerRowIndex;
    maxRows = maxRows == null ? DEFAULT_MAX_ROWS : maxRows;
  }

  @Override
  public void validate() {
    if (headerRowIndex < 0) {
      throw new IllegalArgumentException("headerRowIndex must be non-negative");
    }
    if (maxRows <= 0 || maxRows > MAX_ROWS_LIMIT) {
      throw new IllegalArgumentException("maxRows must be between 1 and " + MAX_ROWS_LIMIT);
    }
  }
}
