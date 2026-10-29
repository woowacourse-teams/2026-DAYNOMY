package org.grit.daynomy.emailsubscription.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface EmailStockContentRepository extends Repository<AssetContent, Long> {
  @Query(
      """
      select c from AssetContent c join fetch c.asset left join fetch c.news
      where c.asset.id in :assetIds and c.sourceType in :types
        and (c.news is null or c.news.status = org.grit.daynomy.news.domain.NewsStatus.PUBLISHED)
        and coalesce(c.news.publishedAt, c.createdAt) >= :from
        and coalesce(c.news.publishedAt, c.createdAt) < :cutoff
      order by coalesce(c.news.publishedAt, c.createdAt) desc, c.id desc
      """)
  List<AssetContent> findRelatedCandidates(
      List<Long> assetIds, List<ContentSourceType> types, Instant from, Instant cutoff);

  @Query(
      """
      select c from AssetContent c join fetch c.asset left join fetch c.news
      where c.id in :ids
        and (c.news is null or c.news.status = org.grit.daynomy.news.domain.NewsStatus.PUBLISHED)
      """)
  List<AssetContent> findCurrentByIds(List<Long> ids);
}
