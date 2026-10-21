package org.grit.daynomy.league.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedPortfolioRepository extends JpaRepository<SharedPortfolio, Long> {
  Optional<SharedPortfolio> findByMemberId(Long memberId);

  List<SharedPortfolio> findAllByMemberIdIn(List<Long> memberIds);
}
