package xiaowu.example.supplieretl.interfaces.rest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaowu.example.supplieretl.datasource.application.service.IngestionJobApplicationService;
import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJob;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;

@RestController
@RequestMapping("/api/examples/data-sources")
@RequiredArgsConstructor
@Tag(name = "Ingestion Job", description = "Data ingestion execution APIs")
public class IngestionJobController {

  private final IngestionJobApplicationService ingestionJobService;

  @PostMapping("/connections/{id}/ingest")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Trigger data ingestion for a saved connection")
  public IngestionJobView triggerIngestion(
      @PathVariable Long id,
      @RequestBody(required = false) JsonNode requestBody) {
    IngestionJob job = ingestionJobService.execute(id, requestBody);
    return toView(job);
  }

  @PostMapping(value = "/excel/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Trigger Excel file ingestion")
  public IngestionJobView triggerExcelIngestion(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "sheetName", required = false) String sheetName,
      @RequestParam(value = "headerRowIndex", required = false) Integer headerRowIndex,
      @RequestParam(value = "maxRows", required = false) Integer maxRows) {
    // 将参数构造为 JsonNode
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var node = mapper.createObjectNode();
    if (sheetName != null) node.put("sheetName", sheetName);
    if (headerRowIndex != null) node.put("headerRowIndex", headerRowIndex);
    if (maxRows != null) node.put("maxRows", maxRows);

    IngestionJob job = ingestionJobService.executeExcel(file, node.isEmpty() ? null : node);
    return toView(job);
  }

  @GetMapping("/jobs/{jobId}")
  @Operation(summary = "Get ingestion job detail")
  public IngestionJobView getJob(@PathVariable Long jobId) {
    return toView(ingestionJobService.getJob(jobId));
  }

  @GetMapping("/connections/{id}/jobs")
  @Operation(summary = "List recent ingestion jobs for a connection")
  public List<IngestionJobView> recentJobs(
      @PathVariable Long id,
      @RequestParam(defaultValue = "10") int limit) {
    return ingestionJobService.recentJobs(id, limit).stream()
        .map(IngestionJobController::toView)
        .toList();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
    return Map.of("message", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String, String> handleInternalError(IllegalStateException ex) {
    return Map.of("message", ex.getMessage());
  }

  private static IngestionJobView toView(IngestionJob job) {
    IngestionResult result = job.getResult();
    return new IngestionJobView(
        job.getId(),
        job.getConnectionId(),
        job.getDataSourceType().name(),
        job.getStatus().name(),
        job.getMessage(),
        result != null ? result.totalRecords() : null,
        result != null ? result.columns() : null,
        result != null ? result.sampleRows() : null,
        result != null ? result.metadata() : null,
        job.getStartedAt(),
        job.getFinishedAt());
  }

  public record IngestionJobView(
      Long id,
      Long connectionId,
      String dataSourceType,
      String status,
      String message,
      Integer totalRecords,
      List<String> columns,
      List<Map<String, Object>> sampleRows,
      Map<String, Object> metadata,
      LocalDateTime startedAt,
      LocalDateTime finishedAt) {
  }
}
