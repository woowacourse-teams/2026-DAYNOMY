package org.grit.daynomy.portfolio.repository;

import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioAnalysisRepository extends JpaRepository<PortfolioAnalysis, Long> {

  Optional<PortfolioAnalysis> findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(
      Long memberId);

  Optional<PortfolioAnalysis> findByIdAndPortfolioMemberId(Long id, Long memberId);

  java.util.List<PortfolioAnalysis> findAllByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(
      Long memberId, org.springframework.data.domain.Pageable pageable);

  @Modifying
  @Query(
      value =
          """
      DELETE FROM portfolio_analyses WHERE id IN (
          SELECT id FROM portfolio_analyses WHERE portfolio_id = :portfolioId
          ORDER BY analyzed_at DESC, id DESC OFFSET :keepCount
      )
      """,
      nativeQuery = true)
  int deleteOlderAnalyses(
      @Param("portfolioId") Long portfolioId, @Param("keepCount") int keepCount);
}
