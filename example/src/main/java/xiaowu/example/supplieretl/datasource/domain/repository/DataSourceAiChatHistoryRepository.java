package xiaowu.example.supplieretl.datasource.domain.repository;

import java.util.List;

import xiaowu.example.supplieretl.datasource.domain.entity.DataSourceAiChatHistory;

public interface DataSourceAiChatHistoryRepository {

  DataSourceAiChatHistory save(DataSourceAiChatHistory history);

  List<DataSourceAiChatHistory> findByConnectionId(Long connectionId, Integer limit);
}
