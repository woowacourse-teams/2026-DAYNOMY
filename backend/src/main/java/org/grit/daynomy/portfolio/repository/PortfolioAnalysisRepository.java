package org.grit.daynomy.portfolio.repository;

import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioAnalysisRepository extends JpaRepository<PortfolioAnalysis, Long> {

  Optional<PortfolioAnalysis> findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(
      Long memberId);

  Optional<PortfolioAnalysis> findByIdAndPortfolioMemberId(Long id, Long memberId);
}
