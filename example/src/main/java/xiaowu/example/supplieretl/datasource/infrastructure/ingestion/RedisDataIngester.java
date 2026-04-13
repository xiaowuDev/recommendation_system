package xiaowu.example.supplieretl.datasource.infrastructure.ingestion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.RedisDataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.RedisIngestionRequest;

@Component
public class RedisDataIngester implements DataIngester {

  @Override
  public DataSourceType supports() {
    return DataSourceType.REDIS;
  }

  @Override
  public IngestionResult ingest(DataSourceConfig config, IngestionRequest request) {
    RedisDataSourceConfig redisConfig = (RedisDataSourceConfig) config;
    RedisIngestionRequest redisRequest = (RedisIngestionRequest) request;

    RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
        redisConfig.host(), redisConfig.port());
    standalone.setDatabase(redisConfig.database());
    if (redisConfig.password() != null) {
      standalone.setPassword(RedisPassword.of(redisConfig.password()));
    }

    LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
        .commandTimeout(Duration.ofSeconds(10))
        .shutdownTimeout(Duration.ofSeconds(1))
        .build();

    LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, clientConfig);
    try {
      factory.afterPropertiesSet();
      try (RedisConnection conn = factory.getConnection()) {
        return doIngest(conn, redisRequest);
      }
    } finally {
      factory.destroy();
    }
  }

  private IngestionResult doIngest(RedisConnection conn, RedisIngestionRequest request) {
    ScanOptions options = ScanOptions.scanOptions()
        .match(request.keyPattern())
        .count(request.scanCount())
        .build();

    List<Map<String, Object>> rows = new ArrayList<>();
    List<String> columns = List.of("key", "type", "value");

    try (Cursor<byte[]> cursor = conn.keyCommands().scan(options)) {
      while (cursor.hasNext() && rows.size() < request.maxKeys()) {
        byte[] keyBytes = cursor.next();
        String key = new String(keyBytes);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);

        DataType type = conn.keyCommands().type(keyBytes);
        row.put("type", type != null ? type.code() : "unknown");
        row.put("value", readValue(conn, keyBytes, type));
        rows.add(row);
      }
    }

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("keyPattern", request.keyPattern());
    metadata.put("maxKeys", request.maxKeys());

    return IngestionResult.of(rows.size(), columns, rows, Collections.unmodifiableMap(metadata));
  }

  private static Object readValue(RedisConnection conn, byte[] key, DataType type) {
    if (type == null) return null;
    try {
      return switch (type) {
        case STRING -> {
          byte[] val = conn.stringCommands().get(key);
          yield val == null ? null : new String(val);
        }
        case HASH -> {
          Map<byte[], byte[]> hash = conn.hashCommands().hGetAll(key);
          Map<String, String> result = new LinkedHashMap<>();
          if (hash != null) {
            hash.forEach((k, v) -> result.put(new String(k), new String(v)));
          }
          yield result;
        }
        case LIST -> {
          List<byte[]> list = conn.listCommands().lRange(key, 0, 99);
          yield list == null ? List.of() : list.stream().map(String::new).toList();
        }
        case SET -> {
          Set<byte[]> members = conn.setCommands().sMembers(key);
          yield members == null ? List.of() : members.stream().map(String::new).toList();
        }
        case ZSET -> {
          Set<byte[]> members = conn.zSetCommands().zRange(key, 0, 99);
          yield members == null ? List.of() : members.stream().map(String::new).toList();
        }
        default -> "(unsupported type: " + type.code() + ")";
      };
    } catch (Exception ex) {
      return "(error: " + ex.getMessage() + ")";
    }
  }
}
