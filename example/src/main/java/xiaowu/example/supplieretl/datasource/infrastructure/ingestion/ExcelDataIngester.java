package xiaowu.example.supplieretl.datasource.infrastructure.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.Row;
import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.ExcelIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;

/**
 * Excel 数据接入执行器。
 * 与其他 ingester 不同，Excel 不走网络连接而是解析上传文件，
 * 因此提供额外的 ingestFile 方法接收 MultipartFile。
 */
@Component
public class ExcelDataIngester implements DataIngester {

  @Override
  public DataSourceType supports() {
    return DataSourceType.EXCEL;
  }

  /**
   * 此方法不适用于 Excel（Excel 需要文件上传），调用时请使用 ingestFile。
   */
  @Override
  public IngestionResult ingest(DataSourceConfig config, IngestionRequest request) {
    throw new UnsupportedOperationException(
        "Excel ingestion requires a file upload. Use ingestFile() instead.");
  }

  public IngestionResult ingestFile(MultipartFile file, ExcelIngestionRequest request) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Excel file must not be empty");
    }
    String filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
      throw new IllegalArgumentException("Only .xlsx files are supported");
    }

    try (InputStream is = file.getInputStream();
        Workbook workbook = new XSSFWorkbook(is)) {
      return parseWorkbook(workbook, request, filename);
    } catch (IOException ex) {
      throw new IllegalStateException("Excel ingestion failed: " + ex.getMessage(), ex);
    }
  }

  private IngestionResult parseWorkbook(Workbook workbook, ExcelIngestionRequest request, String filename) {
    List<String> sheetNames = new ArrayList<>();
    for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
      sheetNames.add(workbook.getSheetAt(i).getSheetName());
    }

    Sheet sheet = selectSheet(workbook, request.sheetName());
    List<String> headers = readRowValues(sheet.getRow(request.headerRowIndex()));

    int startRow = request.headerRowIndex() + 1;
    int lastRow = sheet.getLastRowNum();
    DataFormatter formatter = new DataFormatter();
    List<Map<String, Object>> rows = new ArrayList<>();

    for (int rowIdx = startRow; rowIdx <= lastRow && rows.size() < request.maxRows(); rowIdx++) {
      Row row = sheet.getRow(rowIdx);
      if (row == null)
        continue;
      Map<String, Object> rowMap = new LinkedHashMap<>();
      for (int colIdx = 0; colIdx < headers.size(); colIdx++) {
        Cell cell = row.getCell(colIdx);
        rowMap.put(headers.get(colIdx), cell == null ? "" : formatter.formatCellValue(cell));
      }
      rows.add(rowMap);
    }

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("fileName", filename);
    metadata.put("sheetNames", sheetNames);
    metadata.put("selectedSheet", sheet.getSheetName());
    metadata.put("headerRowIndex", request.headerRowIndex());

    return IngestionResult.of(rows.size(), headers, rows, Collections.unmodifiableMap(metadata));
  }

  private static Sheet selectSheet(Workbook workbook, String sheetName) {
    if (sheetName == null) {
      return workbook.getSheetAt(0);
    }
    Sheet sheet = workbook.getSheet(sheetName);
    if (sheet == null) {
      throw new IllegalArgumentException("Sheet not found: " + sheetName);
    }
    return sheet;
  }

  private static List<String> readRowValues(Row row) {
    if (row == null)
      return List.of();
    DataFormatter formatter = new DataFormatter();
    List<String> values = new ArrayList<>();
    for (int i = 0; i < row.getLastCellNum(); i++) {
      Cell cell = row.getCell(i);
      values.add(cell == null ? "column_" + i : formatter.formatCellValue(cell));
    }
    return List.copyOf(values);
  }
}
