package org.grit.daynomy.finance.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.finance.domain.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProgressRepository extends JpaRepository<LearningProgress, Long> {
  Optional<LearningProgress> findByMemberIdAndItemKey(Long memberId, String itemKey);

  List<LearningProgress> findAllByMemberIdOrderByUpdatedAtDesc(Long memberId);
}
