package org.grit.daynomy.content.repository;

import java.util.List;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentCollectionSourceRepository
    extends JpaRepository<ContentCollectionSource, Long> {

  List<ContentCollectionSource> findAllByOrderByIdAsc();

  List<ContentCollectionSource> findAllByEnabledTrueOrderByIdAsc();

  boolean existsBySourceTypeAndSourceUrl(ContentSourceType sourceType, String sourceUrl);

  boolean existsBySourceTypeAndSourceUrlAndIdNot(
      ContentSourceType sourceType, String sourceUrl, Long id);
}
