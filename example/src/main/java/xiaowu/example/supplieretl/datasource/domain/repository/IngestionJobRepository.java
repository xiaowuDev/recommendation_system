package xiaowu.example.supplieretl.datasource.domain.repository;

import java.util.List;
import java.util.Optional;

import xiaowu.example.supplieretl.datasource.domain.entity.IngestionJob;

public interface IngestionJobRepository {

  IngestionJob save(IngestionJob job);

  IngestionJob update(IngestionJob job);

  Optional<IngestionJob> findById(Long id);

  List<IngestionJob> findRecentByConnectionId(Long connectionId, int limit);
}
