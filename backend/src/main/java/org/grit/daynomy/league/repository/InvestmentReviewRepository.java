package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvestmentReviewRepository extends JpaRepository<InvestmentReview, Long> {
  List<InvestmentReview> findAllByTransactionIdOrderByCreatedAtAsc(Long transactionId);

  List<InvestmentReview> findAllByTransactionIdInOrderByCreatedAtAsc(List<Long> transactionIds);

  @Query(
      """
      select count(review) from InvestmentReview review
      where review.transaction.portfolio.id = :portfolioId and review.createdAt >= :from
        and exists (select holding.id from SharedHolding holding
          where holding.portfolio = review.transaction.portfolio
            and holding.asset = review.transaction.asset and holding.hidden = false)
      """)
  long countPublicReviews(Long portfolioId, Instant from);
}
