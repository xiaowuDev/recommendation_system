package xiaowu.example.supplieretl.datasource.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import xiaowu.example.supplieretl.datasource.ai.application.service.DataSourceAiChatApplicationService.AiChatResult;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceAiChatHistory;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.repository.DataSourceAiChatHistoryRepository;

@ExtendWith(MockitoExtension.class)
class DataSourceAiChatHistoryApplicationServiceTest {

  @Mock
  private DataSourceAiChatHistoryRepository historyRepository;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private DataSourceAiChatHistoryApplicationService service;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    service = new DataSourceAiChatHistoryApplicationService(
        historyRepository,
        objectMapper,
        redisTemplate);
  }

  @Test
  void listHistoryShouldReturnRedisCacheForUnlimitedQuery() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("etl:ai:history:connection:1")).thenReturn("""
        [
          {
            "id": 5,
            "connectionId": 1,
            "sessionId": "session-1",
            "userMessage": "prompt",
            "assistantMessage": "assistant",
            "sourceSummary": null,
            "suggestionAvailable": false,
            "suggestedRequest": null,
            "warnings": ["warn"],
            "generatedAt": "2026-04-15T14:00:00",
            "createdAt": "2026-04-15T14:00:01"
          }
        ]
        """);

    List<DataSourceAiChatHistory> histories = service.listHistory(1L, null);

    assertThat(histories).hasSize(1);
    assertThat(histories.get(0).getSessionId()).isEqualTo("session-1");
    verify(historyRepository, never()).findByConnectionId(any(), any());
  }

  @Test
  void listHistoryShouldBypassRedisWhenLimitProvided() {
    when(historyRepository.findByConnectionId(2L, 20)).thenReturn(List.of(
        DataSourceAiChatHistory.create(
            2L,
            "session-2",
            "prompt",
            "assistant",
            null,
            false,
            null,
            List.of(),
            LocalDateTime.of(2026, 4, 15, 15, 0, 0))));

    List<DataSourceAiChatHistory> histories = service.listHistory(2L, 20);

    assertThat(histories).hasSize(1);
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void saveHistoryShouldEvictUnlimitedHistoryCache() {
    AiChatResult result = new AiChatResult(
        "session-3",
        "assistant",
        "summary",
        true,
        new MysqlIngestionRequest("SELECT * FROM item_catalog", 100),
        List.of("warn"),
        LocalDateTime.of(2026, 4, 15, 16, 0, 0));

    when(historyRepository.save(any(DataSourceAiChatHistory.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    DataSourceAiChatHistory saved = service.saveHistory(3L, "session-3", "prompt", result);

    assertThat(saved.getConnectionId()).isEqualTo(3L);
    assertThat(saved.isSuggestionAvailable()).isTrue();
    verify(redisTemplate).delete(eq("etl:ai:history:connection:3"));
  }

  @Test
  void listHistoryShouldEnterCooldownAfterRedisFailure() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("etl:ai:history:connection:4"))
        .thenThrow(new RuntimeException("redis down"));
    when(historyRepository.findByConnectionId(4L, null)).thenReturn(List.of(
        DataSourceAiChatHistory.create(
            4L,
            "session-4",
            "prompt",
            "assistant",
            null,
            false,
            null,
            List.of(),
            LocalDateTime.of(2026, 4, 15, 17, 0, 0))));

    List<DataSourceAiChatHistory> first = service.listHistory(4L, null);
    List<DataSourceAiChatHistory> second = service.listHistory(4L, null);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
    verify(redisTemplate, times(1)).opsForValue();
    verify(historyRepository, times(2)).findByConnectionId(4L, null);
  }
}
