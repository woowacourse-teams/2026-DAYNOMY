package org.grit.daynomy.emailsubscription.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.content.domain.AssetContent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface EmailStockContentRepository extends Repository<AssetContent, Long> {
  @Query(
      """
      select c from AssetContent c join fetch c.asset
      where c.asset.id in :assetIds and c.news is null
        and c.sourceType = org.grit.daynomy.content.domain.ContentSourceType.OTHER
        and c.publishedDate >= :from and c.publishedDate <= :to and c.createdAt < :cutoff
      """)
  List<AssetContent> findDisclosureCandidates(
      List<Long> assetIds, LocalDate from, LocalDate to, Instant cutoff);
}
