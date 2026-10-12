package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {
  Optional<PortfolioTransaction> findByPortfolioIdAndRequestKey(
      Long portfolioId, String requestKey);

  Optional<PortfolioTransaction> findByIdAndPortfolioMemberId(Long id, Long memberId);

  List<PortfolioTransaction> findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(Long memberId);

  List<PortfolioTransaction> findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
      Long portfolioId, LocalDate tradedOn);

  List<PortfolioTransaction>
      findAllByPortfolioIdAndTradedOnLessThanEqualAndCreatedAtLessThanOrderByTradedOnDescIdDesc(
          Long portfolioId, LocalDate tradedOn, Instant createdBefore);
}
