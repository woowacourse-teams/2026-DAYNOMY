package org.grit.daynomy.content.repository;

import java.util.Optional;
import org.grit.daynomy.content.domain.CollectedExternalContent;
import org.grit.daynomy.content.domain.ContentCollectionReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectedExternalContentRepository
    extends JpaRepository<CollectedExternalContent, Long> {

  Optional<CollectedExternalContent> findByUrl(String url);

  @EntityGraph(attributePaths = "source")
  @Query(
      """
      select content
      from CollectedExternalContent content
      where exists (
        select review.id
        from CollectedExternalContentReview review
        where review.collectedContent = content and review.status = :status
      )
      order by content.createdAt desc, content.id desc
      """)
  Page<CollectedExternalContent> findAllWithReviewStatus(
      @Param("status") ContentCollectionReviewStatus status, Pageable pageable);
}
