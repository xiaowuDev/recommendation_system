package xiaowu.example.supplieretl.datasource.application.support;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.ExcelIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.KafkaIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.MysqlIngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.RedisIngestionRequest;

/**
 * IngestionRequest / IngestionResult 与 JSON 之间的转换器
 */
@Component
public class IngestionRequestMapper {

  private final ObjectMapper objectMapper;

  public IngestionRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  public IngestionRequest requestFromJsonNode(DataSourceType type, JsonNode node) {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(node, "node must not be null");
    try {
      IngestionRequest request = objectMapper.treeToValue(node, resolveRequestClass(type));
      request.validate();
      return request;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to parse ingestion request for type " + type, ex);
    }
  }

  public IngestionRequest requestFromJson(DataSourceType type, String json) {
    Objects.requireNonNull(type, "type must not be null");
    if (json == null || json.isBlank()) {
      return defaultRequest(type);
    }
    try {
      IngestionRequest request = objectMapper.readValue(json, resolveRequestClass(type));
      request.validate();
      return request;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to deserialize ingestion request for type " + type, ex);
    }
  }

  public String requestToJson(IngestionRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to serialize ingestion request", ex);
    }
  }

  public IngestionResult resultFromJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, IngestionResult.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to deserialize ingestion result", ex);
    }
  }

  public String resultToJson(IngestionResult result) {
    if (result == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to serialize ingestion result", ex);
    }
  }

  private static Class<? extends IngestionRequest> resolveRequestClass(DataSourceType type) {
    return switch (type) {
      case KAFKA -> KafkaIngestionRequest.class;
      case MYSQL -> MysqlIngestionRequest.class;
      case REDIS -> RedisIngestionRequest.class;
      case EXCEL -> ExcelIngestionRequest.class;
    };
  }

  private static IngestionRequest defaultRequest(DataSourceType type) {
    return switch (type) {
      case KAFKA -> new KafkaIngestionRequest(null, null);
      case MYSQL -> throw new IllegalArgumentException("query is required for MySQL ingestion");
      case REDIS -> new RedisIngestionRequest(null, null, null);
      case EXCEL -> new ExcelIngestionRequest(null, null, null);
    };
  }
}
