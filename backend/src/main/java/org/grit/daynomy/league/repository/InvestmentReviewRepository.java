package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentReviewRepository extends JpaRepository<InvestmentReview, Long> {
  List<InvestmentReview> findAllByTransactionIdOrderByCreatedAtAsc(Long transactionId);

  List<InvestmentReview> findAllByTransactionIdInOrderByCreatedAtAsc(List<Long> transactionIds);

  long countByTransactionPortfolioIdAndCreatedAtGreaterThanEqual(Long portfolioId, Instant from);
}
