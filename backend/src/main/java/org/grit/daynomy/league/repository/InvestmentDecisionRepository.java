package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvestmentDecisionRepository extends JpaRepository<InvestmentDecision, Long> {
  Optional<InvestmentDecision> findByPortfolioIdAndRequestKey(Long portfolioId, String requestKey);

  Optional<InvestmentDecision> findByIdAndPortfolioMemberId(Long id, Long memberId);

  List<InvestmentDecision> findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(Long memberId);

  List<InvestmentDecision> findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
      Long portfolioId, LocalDate tradedOn);

  @EntityGraph(attributePaths = "asset")
  @Query(
      """
      select d from InvestmentDecision d
      where d.portfolio.member.id = :memberId and d.createdAt <= :createdBefore
        and not exists (select r.id from InvestmentReview r where r.transaction = d)
      order by d.createdAt, d.id
      """)
  List<InvestmentDecision> findUnreviewedCreatedBefore(
      Long memberId, Instant createdBefore, Pageable pageable);

  @Query(
      """
      select decision.portfolio.member.id as memberId,
        count(distinct decision.id) as decisionCount,
        count(distinct review.transaction.id) as reviewedCount
      from InvestmentDecision decision
      left join InvestmentReview review on review.transaction.id = decision.id
      where decision.portfolio.member.id in :memberIds and decision.tradedOn <= :tradedOn
      group by decision.portfolio.member.id
      """)
  List<InvestmentDecisionCounts> findCountsByMemberIdInAndTradedOnLessThanEqual(
      List<Long> memberIds, LocalDate tradedOn);
}
