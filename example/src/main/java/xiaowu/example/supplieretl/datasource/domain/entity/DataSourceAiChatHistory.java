package xiaowu.example.supplieretl.datasource.domain.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;

public final class DataSourceAiChatHistory {

  private final Long id;
  private final Long connectionId;
  private final String sessionId;
  private final String userMessage;
  private final String assistantMessage;
  private final String sourceSummary;
  private final boolean suggestionAvailable;
  private final MysqlIngestionRequest suggestedRequest;
  private final List<String> warnings;
  private final LocalDateTime generatedAt;
  private final LocalDateTime createdAt;

  private DataSourceAiChatHistory(
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
    if (id != null && id <= 0) {
      throw new IllegalArgumentException("id must be positive when present");
    }
    if (connectionId == null || connectionId <= 0) {
      throw new IllegalArgumentException("connectionId must be positive");
    }
    this.id = id;
    this.connectionId = connectionId;
    this.sessionId = requireText(sessionId, "sessionId must not be blank");
    this.userMessage = requireText(userMessage, "userMessage must not be blank");
    this.assistantMessage = requireText(assistantMessage, "assistantMessage must not be blank");
    this.sourceSummary = normalizeNullableText(sourceSummary);
    this.suggestionAvailable = suggestionAvailable;
    this.suggestedRequest = suggestedRequest;
    this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  public static DataSourceAiChatHistory create(
      Long connectionId,
      String sessionId,
      String userMessage,
      String assistantMessage,
      String sourceSummary,
      boolean suggestionAvailable,
      MysqlIngestionRequest suggestedRequest,
      List<String> warnings,
      LocalDateTime generatedAt) {
    return new DataSourceAiChatHistory(
        null,
        connectionId,
        sessionId,
        userMessage,
        assistantMessage,
        sourceSummary,
        suggestionAvailable,
        suggestedRequest,
        warnings,
        generatedAt,
        LocalDateTime.now());
  }

  public static DataSourceAiChatHistory restore(
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
    return new DataSourceAiChatHistory(
        id,
        connectionId,
        sessionId,
        userMessage,
        assistantMessage,
        sourceSummary,
        suggestionAvailable,
        suggestedRequest,
        warnings,
        generatedAt,
        createdAt);
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public Long getId() {
    return id;
  }

  public Long getConnectionId() {
    return connectionId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getUserMessage() {
    return userMessage;
  }

  public String getAssistantMessage() {
    return assistantMessage;
  }

  public String getSourceSummary() {
    return sourceSummary;
  }

  public boolean isSuggestionAvailable() {
    return suggestionAvailable;
  }

  public MysqlIngestionRequest getSuggestedRequest() {
    return suggestedRequest;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public LocalDateTime getGeneratedAt() {
    return generatedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
