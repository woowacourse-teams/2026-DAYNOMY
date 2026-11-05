package org.grit.daynomy.content.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.news.domain.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetContentRepository extends JpaRepository<AssetContent, Long> {

  Page<AssetContent> findAllBySourceTypeInAndNewsIsNullOrderByPublishedDateDescCreatedAtDescIdDesc(
      List<ContentSourceType> sources, Pageable pageable);

  List<AssetContent> findAllByAssetIdOrderByCreatedAtDescIdDesc(Long assetId);

  @Query(
      "select distinct content.asset.id from AssetContent content left join content.news news "
          + "where news is null or news.status = :published order by content.asset.id")
  Slice<Long> findDiscoverableAssetIds(@Param("published") NewsStatus published, Pageable pageable);

  @Query(
      """
      select news.id from AssetContent content join content.news news
      where content.asset.id = :assetId and news.status = :status
        and news.publishedAt > :after and news.publishedAt <= :before
      order by news.publishedAt desc, news.id desc
      """)
  List<Long> findPublishedNewsIdsForAsset(
      @Param("assetId") Long assetId,
      @Param("status") NewsStatus status,
      @Param("after") Instant after,
      @Param("before") Instant before,
      Pageable pageable);

  boolean existsByAssetIdAndUrl(Long assetId, String url);

  Optional<AssetContent> findByIdAndAsset_Id(Long id, Long assetId);

  List<AssetContent> findAllByNewsId(Long newsId);

  @Query("select content.asset.id from AssetContent content where content.news.id = :newsId")
  List<Long> findAssetIdsByNewsId(@Param("newsId") Long newsId);

  void deleteAllByNewsId(Long newsId);
}
