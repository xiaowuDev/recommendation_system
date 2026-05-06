package xiaowu.example.supplieretl.datasource.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import xiaowu.example.supplieretl.datasource.ai.application.service.DataSourceAiChatApplicationService.AiChatResult;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceAiChatHistory;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.repository.DataSourceAiChatHistoryRepository;

@Service
@Slf4j
public class DataSourceAiChatHistoryApplicationService {

  private static final String HISTORY_CACHE_KEY_PREFIX = "etl:ai:history:connection:";
  private static final Duration HISTORY_CACHE_TTL = Duration.ofMinutes(10);
  private static final Duration REDIS_FAILURE_COOLDOWN = Duration.ofMinutes(5);

  private final DataSourceAiChatHistoryRepository historyRepository;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;
  private final AtomicReference<Instant> redisRetryAllowedAt = new AtomicReference<>(Instant.EPOCH);

  public DataSourceAiChatHistoryApplicationService(
      DataSourceAiChatHistoryRepository historyRepository,
      ObjectMapper objectMapper,
      @Nullable StringRedisTemplate redisTemplate) {
    this.historyRepository = historyRepository;
    this.objectMapper = objectMapper;
    this.redisTemplate = redisTemplate;
  }

  public DataSourceAiChatHistory saveHistory(
      Long connectionId,
      String sessionId,
      String userMessage,
      AiChatResult result) {
    DataSourceAiChatHistory history = DataSourceAiChatHistory.create(
        connectionId,
        sessionId,
        userMessage,
        result.assistantMessage(),
        result.sourceSummary(),
        result.suggestionAvailable(),
        result.suggestedRequest(),
        result.warnings(),
        result.generatedAt());
    DataSourceAiChatHistory saved = historyRepository.save(history);
    evictHistoryCache(connectionId);
    return saved;
  }

  public List<DataSourceAiChatHistory> listHistory(Long connectionId, Integer limit) {
    validateConnectionId(connectionId);
    if (limit != null && limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    if (limit != null) {
      return historyRepository.findByConnectionId(connectionId, limit);
    }

    List<DataSourceAiChatHistory> cachedHistory = readHistoryCache(connectionId);
    if (cachedHistory != null) {
      return cachedHistory;
    }

    List<DataSourceAiChatHistory> history = historyRepository.findByConnectionId(connectionId, null);
    writeHistoryCache(connectionId, history);
    return history;
  }

  private void validateConnectionId(Long connectionId) {
    if (connectionId == null || connectionId <= 0) {
      throw new IllegalArgumentException("connectionId must be positive");
    }
  }

  private void evictHistoryCache(Long connectionId) {
    if (!isRedisReady()) {
      return;
    }
    try {
      redisTemplate.delete(historyCacheKey(connectionId));
    } catch (Exception ex) {
      handleRedisFailure("evict", connectionId, ex);
    }
  }

  @Nullable
  private List<DataSourceAiChatHistory> readHistoryCache(Long connectionId) {
    if (!isRedisReady()) {
      return null;
    }
    try {
      String cachedJson = redisTemplate.opsForValue().get(historyCacheKey(connectionId));
      if (cachedJson == null || cachedJson.isBlank()) {
        return null;
      }
      List<AiChatHistoryCacheItem> cachedItems = objectMapper.readValue(cachedJson, new TypeReference<>() {
      });
      return cachedItems.stream()
          .map(AiChatHistoryCacheItem::toEntity)
          .toList();
    } catch (Exception ex) {
      handleRedisFailure("read", connectionId, ex);
      return null;
    }
  }

  private void writeHistoryCache(Long connectionId, List<DataSourceAiChatHistory> history) {
    if (!isRedisReady()) {
      return;
    }
    try {
      List<AiChatHistoryCacheItem> cacheItems = history.stream()
          .map(AiChatHistoryCacheItem::fromEntity)
          .toList();
      redisTemplate.opsForValue().set(
          historyCacheKey(connectionId),
          objectMapper.writeValueAsString(cacheItems),
          HISTORY_CACHE_TTL);
    } catch (Exception ex) {
      handleRedisFailure("write", connectionId, ex);
    }
  }

  private boolean isRedisReady() {
    if (redisTemplate == null) {
      return false;
    }
    Instant retryAt = redisRetryAllowedAt.get();
    return !Instant.now().isBefore(retryAt);
  }

  private void handleRedisFailure(String operation, Long connectionId, Exception ex) {
    Instant nextRetryAt = Instant.now().plus(REDIS_FAILURE_COOLDOWN);
    redisRetryAllowedAt.set(nextRetryAt);
    log.warn(
        "AI chat history Redis cache {} failed for connectionId={}. Falling back to DB and pausing Redis cache access until {}. Cause: {}",
        operation,
        connectionId,
        nextRetryAt,
        ex.getMessage());
    log.debug("AI chat history Redis cache failure stack", ex);
  }

  private String historyCacheKey(Long connectionId) {
    return HISTORY_CACHE_KEY_PREFIX + connectionId;
  }

  record AiChatHistoryCacheItem(
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

    static AiChatHistoryCacheItem fromEntity(DataSourceAiChatHistory history) {
      return new AiChatHistoryCacheItem(
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

    DataSourceAiChatHistory toEntity() {
      return DataSourceAiChatHistory.restore(
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
  }
}
