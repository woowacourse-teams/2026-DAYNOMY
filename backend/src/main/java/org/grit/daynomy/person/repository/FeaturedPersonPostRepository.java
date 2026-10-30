package org.grit.daynomy.person.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeaturedPersonPostRepository extends JpaRepository<FeaturedPersonPost, Long> {

  boolean existsByIdAndStatus(Long id, FeaturedPersonPostStatus status);

  List<FeaturedPersonPost> findAllByStatusOrderByCreatedAtDescIdDesc(
      FeaturedPersonPostStatus status);

  List<FeaturedPersonPost> findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(
      Long personId, FeaturedPersonPostStatus status);

  @EntityGraph(attributePaths = "person")
  @Query(
      """
      SELECT post
      FROM FeaturedPersonPost post
      WHERE post.status = :status
      ORDER BY
        CASE WHEN post.publishedAt IS NULL THEN 1 ELSE 0 END,
        post.publishedAt DESC,
        post.createdAt DESC,
        post.id DESC
      """)
  Page<FeaturedPersonPost> findPublicPageByStatus(
      @Param("status") FeaturedPersonPostStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "person")
  @Query(
      """
      SELECT post
      FROM FeaturedPersonPost post
      WHERE post.person.id = :personId
        AND post.status = :status
      ORDER BY
        CASE WHEN post.publishedAt IS NULL THEN 1 ELSE 0 END,
        post.publishedAt DESC,
        post.createdAt DESC,
        post.id DESC
      """)
  Page<FeaturedPersonPost> findPublicPageByPersonIdAndStatus(
      @Param("personId") Long personId,
      @Param("status") FeaturedPersonPostStatus status,
      Pageable pageable);

  @EntityGraph(attributePaths = "person")
  Page<FeaturedPersonPost> findByStatusOrderByCreatedAtDescIdDesc(
      FeaturedPersonPostStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "person")
  Page<FeaturedPersonPost> findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(
      Long personId, FeaturedPersonPostStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "person")
  Page<FeaturedPersonPost> findByStatusAndSourceTypeOrderByCreatedAtDescIdDesc(
      FeaturedPersonPostStatus status, ContentSourceType sourceType, Pageable pageable);

  @EntityGraph(attributePaths = "person")
  Page<FeaturedPersonPost> findByPersonIdAndStatusAndSourceTypeOrderByCreatedAtDescIdDesc(
      Long personId,
      FeaturedPersonPostStatus status,
      ContentSourceType sourceType,
      Pageable pageable);

  boolean existsByPersonIdAndUrl(Long personId, String url);

  Optional<FeaturedPersonPost> findByIdAndPersonId(Long id, Long personId);
}
