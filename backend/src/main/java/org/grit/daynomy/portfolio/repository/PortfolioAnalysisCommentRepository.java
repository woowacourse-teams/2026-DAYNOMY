package org.grit.daynomy.portfolio.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioAnalysisCommentRepository
    extends JpaRepository<PortfolioAnalysisComment, Long> {

  Optional<PortfolioAnalysisComment> findByIdAndShareId(Long id, Long shareId);

  Optional<PortfolioAnalysisComment> findByIdAndAuthorId(Long id, Long authorId);

  List<PortfolioAnalysisComment> findAllByShareIdAndParentIsNullOrderByCreatedAtAscIdAsc(
      Long shareId);

  List<PortfolioAnalysisComment> findAllByShareIdAndAuthorIdAndParentIsNullOrderByCreatedAtAscIdAsc(
      Long shareId, Long authorId);

  List<PortfolioAnalysisComment> findAllByParentIdOrderByCreatedAtAscIdAsc(Long parentId);
}
