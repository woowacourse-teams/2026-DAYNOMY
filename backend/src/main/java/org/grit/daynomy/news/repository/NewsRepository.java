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

public interface NewsRepository extends JpaRepository<News, Long> {

  Page<News> findByStatusOrderByPublishedAtDescIdDesc(NewsStatus status, Pageable pageable);

  Page<News> findByStatusAndCategoryOrderByPublishedAtDescIdDesc(
      NewsStatus status, Category category, Pageable pageable);

  Page<News>
      findByStatusAndPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
          NewsStatus status, Instant startInclusive, Instant endExclusive, Pageable pageable);

  Optional<News> findByIdAndStatus(Long id, NewsStatus status);

  boolean existsBySourceAndExternalId(NewsSource source, String externalId);
}
