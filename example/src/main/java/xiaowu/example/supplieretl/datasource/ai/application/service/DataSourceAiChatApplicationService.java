package xiaowu.example.supplieretl.datasource.ai.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import xiaowu.example.supplieretl.datasource.application.service.DataSourceConnectionApplicationService;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceConnection;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlDataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.transform.MysqlTransformRuleRegistry;

@Service
@Slf4j
public class DataSourceAiChatApplicationService {

  static final String RESULT_MARKER = "<<<RESULT_JSON>>>";
  static final String DEFAULT_ASSISTANT_MESSAGE = "I reviewed the saved MySQL connection. You can continue refining the request.";
  static final String STRUCTURED_RESULT_MISSING_WARNING = "AI did not return a structured result block.";
  static final String STRUCTURED_RESULT_EMPTY_WARNING = "AI returned an empty structured result.";
  static final String STRUCTURED_RESULT_UNREADABLE_WARNING = "AI returned an unreadable structured result.";

  private static final String CONVERSATION_PREFIX = "datasource-ai-stream:";
  private static final MysqlTransformRuleRegistry TRANSFORM_RULE_REGISTRY = MysqlTransformRuleRegistry
      .defaultRegistry();
  private static final Set<String> STRUCTURED_RESULT_WARNINGS = Set.of(
      STRUCTURED_RESULT_MISSING_WARNING,
      STRUCTURED_RESULT_EMPTY_WARNING,
      STRUCTURED_RESULT_UNREADABLE_WARNING);

  private final DataSourceConnectionApplicationService connectionService;
  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;

  public DataSourceAiChatApplicationService(
      DataSourceConnectionApplicationService connectionService,
      @Qualifier("dataSourceAiChatClient") ChatClient chatClient,
      ObjectMapper objectMapper) {
    this.connectionService = connectionService;
    this.chatClient = chatClient;
    this.objectMapper = objectMapper;
  }

  public PreparedChatRequest prepareChat(Long connectionId, String sessionId, String message) {
    if (connectionId == null || connectionId <= 0) {
      throw new IllegalArgumentException("connectionId must be positive");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }

    DataSourceConnection connection = connectionService.getConnection(connectionId);
    requireMysqlConnection(connection);

    String resolvedSessionId = sessionId == null || sessionId.isBlank()
        ? UUID.randomUUID().toString()
        : sessionId.trim();

    return new PreparedChatRequest(
        connectionId,
        connection.getConnectionName(),
        resolvedSessionId,
        message.trim(),
        buildSystemPrompt(connection));
  }

  public Flux<AiStreamEvent> streamChat(PreparedChatRequest preparedChatRequest) {
    StreamAccumulator accumulator = new StreamAccumulator(preparedChatRequest.sessionId());
    String conversationId = buildConversationId(preparedChatRequest);
    return prompt(preparedChatRequest, conversationId)
        .stream()
        .content()
        .filter(content -> content != null)
        .concatMap(content -> Flux.fromIterable(accumulator.accept(content)))
        .concatWith(Flux.defer(() -> Flux.just(
            maybeRecoverStructuredResult(preparedChatRequest, conversationId, accumulator.finish()))));
  }

  private ChatClient.ChatClientRequestSpec prompt(
      PreparedChatRequest preparedChatRequest,
      String conversationId) {
    return chatClient.prompt()
        .system(preparedChatRequest.systemPrompt())
        .user(preparedChatRequest.userMessage())
        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
        .toolContext(Map.of(
            "connectionId", preparedChatRequest.connectionId(),
            "connectionName", preparedChatRequest.connectionName()));
  }

  private void requireMysqlConnection(DataSourceConnection connection) {
    if (connection.getType() != DataSourceType.MYSQL) {
      throw new IllegalArgumentException("AI assistant currently supports saved MySQL connections only");
    }
    if (!(connection.getConfig() instanceof MysqlDataSourceConfig)) {
      throw new IllegalArgumentException("Connection config is not MySQL");
    }
  }

  private String buildConversationId(PreparedChatRequest preparedChatRequest) {
    return CONVERSATION_PREFIX + preparedChatRequest.connectionId() + ":" + preparedChatRequest.sessionId();
  }

  private String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void addWarning(List<String> warnings, String warning) {
    if (!warnings.contains(warning)) {
      warnings.add(warning);
    }
  }

  static String normalizeWriteModeAlias(String writeMode) {
    if (writeMode == null || writeMode.isBlank()) {
      return writeMode;
    }

    return switch (writeMode.trim().toUpperCase(Locale.ROOT)) {
      case "APPEND", "INSERT", "INSERT_ONLY", "CREATE", "CREATE_ONLY", "LOAD", "LOAD_APPEND", "APPEND_ONLY" -> "APPEND";
      case "UPSERT", "MERGE", "UPDATE_OR_INSERT", "UPDATE_INSERT", "INSERT_OR_UPDATE" -> "UPSERT";
      case "REPLACE", "OVERWRITE", "TRUNCATE_INSERT", "TRUNCATE_AND_LOAD", "REBUILD" -> "REPLACE";
      default -> writeMode.trim().toUpperCase(Locale.ROOT);
    };
  }

  static String normalizeFilterOperatorAlias(String operator) {
    if (operator == null || operator.isBlank()) {
      return operator;
    }

    return switch (operator.trim().toUpperCase(Locale.ROOT)) {
      case "EQ", "EQUAL", "EQUALS", "=" -> "EQ";
      case "NE", "NOT_EQUAL", "NOT_EQUALS", "!=", "<>" -> "NE";
      case "GT", "GREATER_THAN", "AFTER", ">" -> "GT";
      case "GTE", "GE", "GREATER_THAN_OR_EQUAL", ">=" -> "GTE";
      case "LT", "LESS_THAN", "BEFORE", "<" -> "LT";
      case "LTE", "LE", "LESS_THAN_OR_EQUAL", "<=" -> "LTE";
      case "LIKE", "CONTAINS", "MATCHES" -> "LIKE";
      case "IN", "IN_LIST" -> "IN";
      case "IS_NULL", "NULL", "ISNULL" -> "IS_NULL";
      case "NOT_NULL", "IS_NOT_NULL", "NOTNULL", "ISNOTNULL" -> "NOT_NULL";
      default -> operator.trim().toUpperCase(Locale.ROOT);
    };
  }

  private MysqlIngestionRequest normalizeSuggestedRequest(MysqlIngestionRequest request) {
    if (request == null) {
      return null;
    }

    MysqlIngestionRequest.TargetConfig target = request.target() == null
        ? MysqlIngestionRequest.TargetConfig.empty()
        : request.target();
    return new MysqlIngestionRequest(
        request.query(),
        request.sourceTable(),
        request.fieldMappings(),
        request.filters() == null
            ? List.of()
            : request.filters().stream()
                .map(filter -> new MysqlIngestionRequest.FilterRule(
                    filter.logic(),
                    filter.field(),
                    normalizeFilterOperatorAlias(filter.operator()),
                    filter.value(),
                    filter.enabled()))
                .toList(),
        request.transformRules(),
        new MysqlIngestionRequest.TargetConfig(
            target.targetName(),
            normalizeWriteModeAlias(target.writeMode()),
            target.primaryKey(),
            target.incrementalField()),
        request.sortField(),
        request.sortDirection(),
        request.maxRows());
  }

  private AiChatResult toAiChatResult(String sessionId, String assistantMessage, AssistantOutput output) {
    List<String> warnings = new ArrayList<>();
    if (output.warnings() != null) {
      warnings.addAll(output.warnings());
    }

    boolean suggestionAvailable = Boolean.TRUE.equals(output.suggestionAvailable());
    MysqlIngestionRequest suggestedRequest = normalizeSuggestedRequest(output.suggestedRequest());
    if (suggestionAvailable) {
      if (suggestedRequest == null) {
        warnings.add("AI did not return a valid ingestion suggestion.");
        suggestionAvailable = false;
      } else {
        try {
          suggestedRequest.validate();
        } catch (IllegalArgumentException ex) {
          warnings.add("AI suggestion needs adjustment before apply: " + ex.getMessage());
          suggestionAvailable = false;
        }
      }
    }

    String resolvedAssistantMessage = assistantMessage == null || assistantMessage.isBlank()
        ? DEFAULT_ASSISTANT_MESSAGE
        : assistantMessage.trim();

    return new AiChatResult(
        sessionId,
        resolvedAssistantMessage,
        normalizeText(output.sourceSummary()),
        suggestionAvailable,
        suggestedRequest,
        List.copyOf(warnings),
        LocalDateTime.now());
  }

  private AssistantOutput parseAssistantOutput(String jsonPayload, List<String> warnings) {
    if (jsonPayload == null || jsonPayload.isBlank()) {
      addWarning(warnings, STRUCTURED_RESULT_MISSING_WARNING);
      return new AssistantOutput(null, false, List.copyOf(warnings), null);
    }

    try {
      AssistantOutput output = objectMapper.readValue(jsonPayload, AssistantOutput.class);
      if (output == null) {
        addWarning(warnings, STRUCTURED_RESULT_EMPTY_WARNING);
        return new AssistantOutput(null, false, List.copyOf(warnings), null);
      }
      if (warnings.isEmpty()) {
        return output;
      }

      List<String> mergedWarnings = new ArrayList<>(warnings);
      if (output.warnings() != null) {
        mergedWarnings.addAll(output.warnings());
      }
      return new AssistantOutput(
          output.sourceSummary(),
          output.suggestionAvailable(),
          List.copyOf(mergedWarnings),
          output.suggestedRequest());
    } catch (JsonProcessingException ex) {
      log.warn("Failed to parse AI structured result: {}", jsonPayload, ex);
      addWarning(warnings, STRUCTURED_RESULT_UNREADABLE_WARNING);
      return new AssistantOutput(null, false, List.copyOf(warnings), null);
    }
  }

  private AiChatResult maybeRecoverStructuredResult(
      PreparedChatRequest preparedChatRequest,
      String conversationId,
      AiChatResult currentResult) {
    if (!needsStructuredRecovery(currentResult)) {
      return currentResult;
    }

    try {
      log.info(
          "Attempting structured AI result recovery, connectionId={}, sessionId={}",
          preparedChatRequest.connectionId(),
          preparedChatRequest.sessionId());
      AssistantOutput recoveredOutput = recoverStructuredOutput(preparedChatRequest, conversationId);
      AiChatResult recoveredResult = toAiChatResult(
          preparedChatRequest.sessionId(),
          currentResult.assistantMessage(),
          recoveredOutput);
      if (!needsStructuredRecovery(recoveredResult)) {
        return recoveredResult;
      }
    } catch (Exception ex) {
      log.warn(
          "Structured AI result recovery failed, connectionId={}, sessionId={}",
          preparedChatRequest.connectionId(),
          preparedChatRequest.sessionId(),
          ex);
    }

    return currentResult;
  }

  private boolean needsStructuredRecovery(AiChatResult currentResult) {
    return currentResult.warnings().stream()
        .anyMatch(STRUCTURED_RESULT_WARNINGS::contains);
  }

  private AssistantOutput recoverStructuredOutput(
      PreparedChatRequest preparedChatRequest,
      String conversationId) {
    String recoveryResponse = chatClient.prompt()
        .system(buildStructuredRecoverySystemPrompt())
        .user(buildStructuredRecoveryUserPrompt(preparedChatRequest.userMessage()))
        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
        .toolContext(Map.of(
            "connectionId", preparedChatRequest.connectionId(),
            "connectionName", preparedChatRequest.connectionName()))
        .call()
        .content();

    StructuredResultExtraction extraction = extractStructuredResult(recoveryResponse, null);
    List<String> warnings = new ArrayList<>(extraction.warnings());
    return parseAssistantOutput(extraction.jsonPayload(), warnings);
  }

  private String buildStructuredRecoverySystemPrompt() {
    return """
        You are repairing the final structured output for a MySQL ingestion assistant conversation.
        The chat memory for this conversation already contains the user's latest request and the inspected MySQL metadata.

        Return exactly one JSON object and nothing else.
        Do not output markdown, code fences, natural-language commentary, or extra labels.

        The JSON object must contain exactly these top-level fields:
        - sourceSummary: string or null
        - suggestionAvailable: boolean
        - warnings: string[]
        - suggestedRequest: MysqlIngestionRequest or null

        Rules:
        1. Never invent table names, field names, filters, transforms, or sample values.
        2. Prefer visual rule-builder mode unless custom SQL is explicitly required.
        3. Supported transformType values are exactly:
        %s
        4. Supported target.writeMode values are exactly APPEND, UPSERT, and REPLACE.
        5. Supported filter operators are exactly EQ, NE, GT, GTE, LT, LTE, LIKE, IN, IS_NULL, and NOT_NULL.
        6. Use NOT_NULL instead of IS_NOT_NULL.
        7. If you can suggest a config, suggestedRequest must match the backend MysqlIngestionRequest shape exactly.
        8. If the latest user turn is only asking a question instead of asking for autofill, set suggestionAvailable to false and suggestedRequest to null.
        9. Do not output any text before or after the JSON object.
        """
        .formatted(TRANSFORM_RULE_REGISTRY.promptSummary());
  }

  private String buildStructuredRecoveryUserPrompt(String userMessage) {
    return """
        The previous assistant turn in this conversation did not include the required structured result.
        Return the final structured result JSON for the latest MySQL ingestion request.

        Latest user request:
        %s
        """
        .formatted(userMessage);
  }

  static StructuredResultExtraction extractStructuredResult(String assistantMessage, String structuredPayload) {
    String visibleAssistantMessage = normalizeNullableText(assistantMessage);
    String explicitPayload = normalizeNullableText(structuredPayload);
    if (explicitPayload != null) {
      return new StructuredResultExtraction(visibleAssistantMessage, explicitPayload, List.of());
    }

    JsonCandidate candidate = locateLastStructuredJsonCandidate(assistantMessage);
    if (candidate != null) {
      String assistantText = normalizeNullableText(assistantMessage == null
          ? null
          : assistantMessage.substring(0, candidate.start()));
      return new StructuredResultExtraction(assistantText, candidate.jsonPayload(), List.of());
    }

    return new StructuredResultExtraction(
        visibleAssistantMessage,
        null,
        List.of(STRUCTURED_RESULT_MISSING_WARNING));
  }

  private static JsonCandidate locateLastStructuredJsonCandidate(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }

    List<int[]> objectRanges = new ArrayList<>();
    boolean inString = false;
    boolean escaping = false;
    int depth = 0;
    int start = -1;
    for (int index = 0; index < text.length(); index++) {
      char current = text.charAt(index);
      if (inString) {
        if (escaping) {
          escaping = false;
        } else if (current == '\\') {
          escaping = true;
        } else if (current == '"') {
          inString = false;
        }
        continue;
      }

      if (current == '"') {
        inString = true;
        continue;
      }
      if (current == '{') {
        if (depth == 0) {
          start = index;
        }
        depth++;
        continue;
      }
      if (current == '}' && depth > 0) {
        depth--;
        if (depth == 0 && start >= 0) {
          objectRanges.add(new int[] { start, index + 1 });
          start = -1;
        }
      }
    }

    for (int index = objectRanges.size() - 1; index >= 0; index--) {
      int[] range = objectRanges.get(index);
      String candidate = text.substring(range[0], range[1]).trim();
      if (looksLikeStructuredAssistantJson(candidate)) {
        return new JsonCandidate(range[0], range[1], candidate);
      }
    }
    return null;
  }

  private static boolean looksLikeStructuredAssistantJson(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return false;
    }
    return candidate.contains("\"suggestionAvailable\"")
        && candidate.contains("\"suggestedRequest\"");
  }

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String buildSystemPrompt(DataSourceConnection connection) {
    return """
                You are xiaowu, a careful assistant focused on helping users configure MySQL ingestion rules.

                Your active saved connection:
                - connectionName: %s
                - connectionType: %s

                You must follow these rules:
                1. Before recommending a MySQL ingestion config, call the MySQL metadata tools to inspect real tables, fields, and sample rows.
                2. Never invent table names, field names, filters, or sample values that do not appear in tool results.
                3. Prefer visual rule-builder mode:
                   - sourceTable
                   - fieldMappings
                   - filters
                   - transformRules
                   - target
                   - sortField
                   - sortDirection
                   - maxRows
                4. Only use the raw query field when the user explicitly asks for custom SQL or the extraction logic truly cannot be expressed by the rule builder.
                5. If sample rows are unavailable, you may still build a suggestion from fields only, but mention that clearly in warnings.
                6. Keep the assistant message concise, in Chinese, and practical.
                7. Supported transformType values are exactly:
        %s
                8. For ADD / SUBTRACT / MULTIPLY / DIVIDE, argument must be a numeric string.
                9. For ROUND, argument should be an integer scale like 0, 2, or 4. If omitted, ROUND means scale 0.
                10. Do not output unsupported transform types such as CAST, EXPRESSION, FORMAT, or MULTIPLY_BY_100.
                11. Supported target.writeMode values are exactly APPEND, UPSERT, and REPLACE.
                12. When the user wants insert-only loading, use APPEND. When the user wants overwrite/truncate-and-reload, use REPLACE. When the user wants merge-by-key semantics, use UPSERT.
                13. Supported filter operators are exactly EQ, NE, GT, GTE, LT, LTE, LIKE, IN, IS_NULL, and NOT_NULL.
                14. Use NOT_NULL instead of IS_NOT_NULL.
                15. Reply in two parts using this exact protocol:
                    - First output only the assistant reply in Chinese plain text. Do not output JSON or code fences in this part.
                    - Then output a new line that contains exactly %s
                    - Then output exactly one JSON object with these fields:
                      sourceSummary: string or null
                      suggestionAvailable: boolean
                      warnings: string[]
                      suggestedRequest: MysqlIngestionRequest or null
                16. If the user is only asking a question and not asking for autofill, set suggestionAvailable to false and suggestedRequest to null.
                17. If you can suggest a config, make sure suggestedRequest matches the current backend MysqlIngestionRequest shape exactly.
                18. Do not output any extra text after the JSON object.
                """
        .formatted(
            connection.getConnectionName(),
            connection.getType().name(),
            TRANSFORM_RULE_REGISTRY.promptSummary(),
            RESULT_MARKER);
  }

  public record PreparedChatRequest(
      Long connectionId,
      String connectionName,
      String sessionId,
      String userMessage,
      String systemPrompt) {
  }

  public sealed interface AiStreamEvent permits AssistantDelta, AiChatResult {
  }

  public record AssistantDelta(String content) implements AiStreamEvent {
  }

  public record AiChatResult(
      String sessionId,
      String assistantMessage,
      String sourceSummary,
      boolean suggestionAvailable,
      MysqlIngestionRequest suggestedRequest,
      List<String> warnings,
      LocalDateTime generatedAt) implements AiStreamEvent {
  }

  public record AssistantOutput(
      String sourceSummary,
      Boolean suggestionAvailable,
      List<String> warnings,
      MysqlIngestionRequest suggestedRequest) {
  }

  record StructuredResultExtraction(
      String assistantMessage,
      String jsonPayload,
      List<String> warnings) {
  }

  private record JsonCandidate(
      int start,
      int end,
      String jsonPayload) {
  }

  private final class StreamAccumulator {

    private final String sessionId;
    private final StringBuilder assistantMessage = new StringBuilder();
    private final StringBuilder structuredPayload = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();
    private boolean markerSeen;

    private StreamAccumulator(String sessionId) {
      this.sessionId = sessionId;
    }

    private List<AiStreamEvent> accept(String chunk) {
      if (markerSeen) {
        structuredPayload.append(chunk);
        return List.of();
      }

      pending.append(chunk);
      int markerIndex = pending.indexOf(RESULT_MARKER);
      if (markerIndex >= 0) {
        List<AiStreamEvent> events = new ArrayList<>();
        String visibleContent = pending.substring(0, markerIndex);
        appendAssistantContent(events, visibleContent);
        structuredPayload.append(pending.substring(markerIndex + RESULT_MARKER.length()));
        pending.setLength(0);
        markerSeen = true;
        return List.copyOf(events);
      }

      int safeLength = pending.length() - RESULT_MARKER.length() + 1;
      if (safeLength <= 0) {
        return List.of();
      }

      String visibleContent = pending.substring(0, safeLength);
      pending.delete(0, safeLength);
      return appendAssistantContent(new ArrayList<>(), visibleContent);
    }

    private AiChatResult finish() {
      if (!markerSeen && !pending.isEmpty()) {
        assistantMessage.append(pending);
        pending.setLength(0);
      } else if (markerSeen && pending.length() > 0) {
        structuredPayload.append(pending);
        pending.setLength(0);
      }

      StructuredResultExtraction extraction = extractStructuredResult(
          assistantMessage.toString(),
          structuredPayload.toString());
      List<String> parseWarnings = new ArrayList<>(extraction.warnings());
      AssistantOutput output = parseAssistantOutput(extraction.jsonPayload(), parseWarnings);
      return toAiChatResult(sessionId, extraction.assistantMessage(), output);
    }

    private List<AiStreamEvent> appendAssistantContent(List<AiStreamEvent> events, String visibleContent) {
      if (visibleContent == null || visibleContent.isEmpty()) {
        return List.copyOf(events);
      }

      assistantMessage.append(visibleContent);
      events.add(new AssistantDelta(visibleContent));
      return List.copyOf(events);
    }
  }
}
