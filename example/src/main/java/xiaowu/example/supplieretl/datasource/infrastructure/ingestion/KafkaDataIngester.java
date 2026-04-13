package xiaowu.example.supplieretl.datasource.infrastructure.ingestion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Component;

import xiaowu.example.supplieretl.datasource.application.port.DataIngester;
import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceType;
import xiaowu.example.supplieretl.datasource.domain.model.DataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionRequest;
import xiaowu.example.supplieretl.datasource.domain.model.IngestionResult;
import xiaowu.example.supplieretl.datasource.domain.model.KafkaDataSourceConfig;
import xiaowu.example.supplieretl.datasource.domain.model.KafkaIngestionRequest;

@Component
public class KafkaDataIngester implements DataIngester {

  @Override
  public DataSourceType supports() {
    return DataSourceType.KAFKA;
  }

  @Override
  public IngestionResult ingest(DataSourceConfig config, IngestionRequest request) {
    KafkaDataSourceConfig kafkaConfig = (KafkaDataSourceConfig) config;
    KafkaIngestionRequest kafkaRequest = (KafkaIngestionRequest) request;

    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaRequest.maxRecords());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, kafkaRequest.timeoutMs());
    if (kafkaConfig.clientId() != null) {
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaConfig.clientId() + "-ingestion");
    }
    // 临时 group，用完不提交
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "etl-ingestion-temp-" + System.currentTimeMillis());

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      List<PartitionInfo> partitionInfos = consumer.partitionsFor(kafkaConfig.topic());
      if (partitionInfos == null || partitionInfos.isEmpty()) {
        return new IngestionResult(0, List.of(), List.of(),
            Map.of("topic", kafkaConfig.topic(), "error", "Topic not found or has no partitions"));
      }

      Set<TopicPartition> partitions = partitionInfos.stream()
          .map(p -> new TopicPartition(p.topic(), p.partition()))
          .collect(Collectors.toSet());
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(kafkaRequest.timeoutMs()));
      List<Map<String, Object>> rows = new ArrayList<>();
      for (ConsumerRecord<String, String> record : records) {
        if (rows.size() >= kafkaRequest.maxRecords()) break;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", record.key());
        row.put("value", record.value());
        row.put("partition", record.partition());
        row.put("offset", record.offset());
        row.put("timestamp", record.timestamp());
        rows.add(row);
      }

      List<String> columns = List.of("key", "value", "partition", "offset", "timestamp");
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("topic", kafkaConfig.topic());
      metadata.put("partitionCount", partitionInfos.size());
      metadata.put("bootstrapServers", kafkaConfig.bootstrapServers());

      return IngestionResult.of(rows.size(), columns, rows, Collections.unmodifiableMap(metadata));
    }
  }
}
