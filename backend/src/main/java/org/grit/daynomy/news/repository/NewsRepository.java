package org.grit.daynomy.news.repository;

import java.time.Instant;
import java.util.Optional;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<News, Long> {

  Page<News> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

  Page<News> findByCategoryOrderByPublishedAtDescIdDesc(Category category, Pageable pageable);

  Optional<News>
      findFirstByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
          Instant startInclusive, Instant endExclusive);

  boolean existsBySourceAndExternalId(NewsSource source, String externalId);
}
