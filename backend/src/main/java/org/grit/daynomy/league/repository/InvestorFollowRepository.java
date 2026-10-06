package org.grit.daynomy.league.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.InvestorFollow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorFollowRepository extends JpaRepository<InvestorFollow, Long> {
  Optional<InvestorFollow> findByFollowerIdAndProfileId(Long followerId, Long profileId);

  List<InvestorFollow> findAllByFollowerIdOrderByCreatedAtDesc(Long followerId);
}
