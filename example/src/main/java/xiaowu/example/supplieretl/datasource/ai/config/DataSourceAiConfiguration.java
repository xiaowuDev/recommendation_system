package xiaowu.example.supplieretl.datasource.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import xiaowu.example.supplieretl.datasource.ai.tool.MysqlMetadataTools;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class DataSourceAiConfiguration {

  @Bean
  ChatMemory dataSourceAiChatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
    log.info("Initializing Spring AI chat memory with JDBC repository");
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(jdbcChatMemoryRepository)
        .maxMessages(24)
        .build();
  }

  @Bean("dataSourceAiChatClient")
  ChatClient dataSourceAiChatClient(
      ChatClient.Builder builder,
      ChatMemory dataSourceAiChatMemory,
      MysqlMetadataTools mysqlMetadataTools) {
    return builder.clone()
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(dataSourceAiChatMemory).build())
        .defaultTools(mysqlMetadataTools)
        .build();
  }
}
