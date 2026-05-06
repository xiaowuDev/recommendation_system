package xiaowu.example.supplieretl.interfaces.rest;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaowu.example.supplieretl.datasource.ai.application.service.DataSourceAiChatApplicationService;
import xiaowu.example.supplieretl.datasource.ai.application.service.DataSourceAiChatApplicationService.AssistantDelta;
import xiaowu.example.supplieretl.datasource.ai.application.service.DataSourceAiChatApplicationService.AiChatResult;
import xiaowu.example.supplieretl.datasource.ai.application.service.DataSourceAiChatApplicationService.PreparedChatRequest;
import xiaowu.example.supplieretl.datasource.application.service.DataSourceAiChatHistoryApplicationService;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceAiChatHistory;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;

@RestController
@RequestMapping("/api/examples/data-sources")
@RequiredArgsConstructor
@Tag(name = "Data Source AI", description = "AI assistant APIs for saved data source connections")
@Slf4j
public class DataSourceAiController {

  private final DataSourceAiChatApplicationService aiChatApplicationService;
  private final DataSourceAiChatHistoryApplicationService aiChatHistoryApplicationService;

  @PostMapping(value = "/connections/{id}/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "Stream the AI assistant reply via SSE and emit the final structured suggestion as the result event")
  public SseEmitter streamChat(
      @PathVariable Long id,
      @RequestBody AiChatRequest request) {
    SseEmitter emitter = new SseEmitter(0L);
    Thread.startVirtualThread(() -> streamChat(emitter, id, request));
    return emitter;
  }

  private void streamChat(SseEmitter emitter, Long id, AiChatRequest request) {
    try {
      PreparedChatRequest preparedChatRequest = aiChatApplicationService.prepareChat(id, request.sessionId(),
          request.message());
      sendEvent(emitter, "session", new AiChatSessionEvent(preparedChatRequest.sessionId()));

      aiChatApplicationService.streamChat(preparedChatRequest)
          .doOnNext(event -> {
            if (event instanceof AssistantDelta delta) {
              sendEvent(emitter, "delta", new AiChatDeltaEvent(delta.content()));
              return;
            }
            if (event instanceof AiChatResult result) {
              persistHistoryQuietly(preparedChatRequest, result);
              sendEvent(emitter, "result", toView(result));
            }
          })
          .blockLast();

      sendEvent(emitter, "done", new AiChatDoneEvent("completed"));
      emitter.complete();
    } catch (IllegalArgumentException ex) {
      log.warn("AI SSE request rejected: {}", ex.getMessage());
      completeWithError(emitter, HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IllegalStateException ex) {
      log.warn("AI SSE request failed: {}", ex.getMessage());
      completeWithError(emitter, HttpStatus.CONFLICT, ex.getMessage());
    } catch (Exception ex) {
      log.error("AI SSE streaming failed", ex);
      completeWithError(emitter, HttpStatus.INTERNAL_SERVER_ERROR, "AI streaming failed");
    }
  }

  private AiChatView toView(AiChatResult result) {
    return new AiChatView(
        result.sessionId(),
        result.assistantMessage(),
        result.sourceSummary(),
        result.suggestionAvailable(),
        result.suggestedRequest(),
        result.warnings(),
        result.generatedAt());
  }

  @GetMapping("/connections/{id}/ai/chat/history")
  @Operation(summary = "List persisted AI assistant history for a saved connection")
  public List<AiChatHistoryView> history(
      @PathVariable Long id,
      @RequestParam(required = false) Integer limit) {
    return aiChatHistoryApplicationService.listHistory(id, limit).stream()
        .map(this::toHistoryView)
        .toList();
  }

  private AiChatHistoryView toHistoryView(DataSourceAiChatHistory history) {
    return new AiChatHistoryView(
        history.getId(),
        history.getConnectionId(),
        history.getSessionId(),
        history.getUserMessage(),
        history.getAssistantMessage(),
        history.getSourceSummary(),
        history.isSuggestionAvailable(),
        history.getSuggestedRequest(),
        history.getWarnings(),
        history.getGeneratedAt(),
        history.getCreatedAt());
  }

  private void persistHistoryQuietly(PreparedChatRequest preparedChatRequest, AiChatResult result) {
    try {
      aiChatHistoryApplicationService.saveHistory(
          preparedChatRequest.connectionId(),
          preparedChatRequest.sessionId(),
          preparedChatRequest.userMessage(),
          result);
    } catch (Exception ex) {
      log.warn(
          "Persisting AI chat history failed, connectionId={}, sessionId={}",
          preparedChatRequest.connectionId(),
          preparedChatRequest.sessionId(),
          ex);
    }
  }

  private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
    try {
      emitter.send(SseEmitter.event()
          .name(eventName)
          .data(payload, MediaType.APPLICATION_JSON));
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to send SSE event: " + eventName, ex);
    }
  }

  private void completeWithError(SseEmitter emitter, HttpStatus status, String message) {
    try {
      sendEvent(emitter, "error", new AiChatErrorEvent(status.value(), message));
      emitter.complete();
    } catch (Exception ex) {
      emitter.completeWithError(ex);
    }
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
    return Map.of("message", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleIllegalState(IllegalStateException ex) {
    return Map.of("message", ex.getMessage());
  }

  public record AiChatRequest(
      String sessionId,
      String message) {
  }

  public record AiChatView(
      String sessionId,
      String assistantMessage,
      String sourceSummary,
      boolean suggestionAvailable,
      MysqlIngestionRequest suggestedRequest,
      List<String> warnings,
      LocalDateTime generatedAt) {
  }

  public record AiChatHistoryView(
      Long id,
      Long connectionId,
      String sessionId,
      String userMessage,
      String assistantMessage,
      String sourceSummary,
      boolean suggestionAvailable,
      MysqlIngestionRequest suggestedRequest,
      List<String> warnings,
      LocalDateTime generatedAt,
      LocalDateTime createdAt) {
  }

  public record AiChatSessionEvent(String sessionId) {
  }

  public record AiChatDeltaEvent(String content) {
  }

  public record AiChatDoneEvent(String status) {
  }

  public record AiChatErrorEvent(int status, String message) {
  }
}
