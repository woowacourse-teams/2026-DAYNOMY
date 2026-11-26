package org.grit.daynomy.portfolio.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioAnalysisCommentRepository
    extends JpaRepository<PortfolioAnalysisComment, Long> {

  Optional<PortfolioAnalysisComment> findByIdAndShareId(Long id, Long shareId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select comment from PortfolioAnalysisComment comment where comment.id = :id and"
          + " comment.share.id = :shareId")
  Optional<PortfolioAnalysisComment> findByIdAndShareIdForUpdate(
      @Param("id") Long id, @Param("shareId") Long shareId);

  Optional<PortfolioAnalysisComment> findByIdAndAuthorId(Long id, Long authorId);

  List<PortfolioAnalysisComment> findAllByShareIdAndParentIsNullOrderByCreatedAtAscIdAsc(
      Long shareId);

  List<PortfolioAnalysisComment> findAllByShareIdAndAuthorIdAndParentIsNullOrderByCreatedAtAscIdAsc(
      Long shareId, Long authorId);

  List<PortfolioAnalysisComment> findAllByParentIdOrderByCreatedAtAscIdAsc(Long parentId);
}
