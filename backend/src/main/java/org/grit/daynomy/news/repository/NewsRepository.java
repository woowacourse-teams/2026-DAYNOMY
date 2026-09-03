package org.grit.daynomy.news.repository;

import java.time.Instant;
import java.util.Optional;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.domain.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRepository extends JpaRepository<News, Long> {

  Page<News> findByStatusOrderByPublishedAtDescIdDesc(NewsStatus status, Pageable pageable);

  Page<News> findByStatusAndCategoryOrderByPublishedAtDescIdDesc(
      NewsStatus status, Category category, Pageable pageable);

  Page<News>
      findByStatusAndPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
          NewsStatus status, Instant startInclusive, Instant endExclusive, Pageable pageable);

  Optional<News> findByIdAndStatus(Long id, NewsStatus status);

  @Query(
      """
      SELECT n
      FROM News n
      WHERE (:status IS NULL OR n.status = :status)
        AND (:category IS NULL OR n.category = :category)
      ORDER BY n.createdAt DESC, n.id DESC
      """)
  Page<News> findAdminNews(
      @Param("status") NewsStatus status, @Param("category") Category category, Pageable pageable);

  boolean existsBySourceAndExternalId(NewsSource source, String externalId);
}
