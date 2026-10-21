package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentDecisionRepository extends JpaRepository<InvestmentDecision, Long> {
  Optional<InvestmentDecision> findByPortfolioIdAndRequestKey(Long portfolioId, String requestKey);

  Optional<InvestmentDecision> findByIdAndPortfolioMemberId(Long id, Long memberId);

  List<InvestmentDecision> findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(Long memberId);

  List<InvestmentDecision> findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
      Long portfolioId, LocalDate tradedOn);

  List<InvestmentDecision> findAllByPortfolioIdInAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
      List<Long> portfolioIds, LocalDate tradedOn);

  List<InvestmentDecision>
      findAllByPortfolioIdAndTradedOnLessThanEqualAndCreatedAtLessThanOrderByTradedOnDescIdDesc(
          Long portfolioId, LocalDate tradedOn, Instant createdBefore);
}
