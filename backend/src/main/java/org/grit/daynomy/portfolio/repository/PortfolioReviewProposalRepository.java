package org.grit.daynomy.portfolio.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioReviewProposalRepository
    extends JpaRepository<PortfolioReviewProposal, Long> {

  @EntityGraph(attributePaths = {"share", "comment"})
  Optional<PortfolioReviewProposal> findByCommentId(Long commentId);

  @EntityGraph(attributePaths = {"share", "comment"})
  List<PortfolioReviewProposal> findAllByShareIdOrderByUpdatedAtDescIdDesc(Long shareId);
}
