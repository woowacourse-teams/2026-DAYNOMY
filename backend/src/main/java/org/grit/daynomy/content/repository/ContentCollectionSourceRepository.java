package org.grit.daynomy.content.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentCollectionSourceRepository
    extends JpaRepository<ContentCollectionSource, Long> {

  List<ContentCollectionSource> findAllByOrderByIdAsc();

  List<ContentCollectionSource> findAllByEnabledTrueOrderByIdAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select source from ContentCollectionSource source where source.id = :sourceId")
  Optional<ContentCollectionSource> findByIdForUpdate(@Param("sourceId") Long sourceId);

  boolean existsBySourceTypeAndSourceUrl(ContentSourceType sourceType, String sourceUrl);

  boolean existsBySourceTypeAndSourceUrlAndIdNot(
      ContentSourceType sourceType, String sourceUrl, Long id);
}
