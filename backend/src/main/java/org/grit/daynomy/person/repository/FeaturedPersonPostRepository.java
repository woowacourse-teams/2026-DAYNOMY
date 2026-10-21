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

public interface FeaturedPersonPostRepository extends JpaRepository<FeaturedPersonPost, Long> {

  boolean existsByIdAndStatus(Long id, FeaturedPersonPostStatus status);

  List<FeaturedPersonPost> findAllByStatusOrderByCreatedAtDescIdDesc(
      FeaturedPersonPostStatus status);

  List<FeaturedPersonPost> findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(
      Long personId, FeaturedPersonPostStatus status);

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
