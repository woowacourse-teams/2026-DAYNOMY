package org.grit.daynomy.content.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.content.domain.AssetContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetContentRepository extends JpaRepository<AssetContent, Long> {

  List<AssetContent> findAllByAssetIdOrderByCreatedAtDescIdDesc(Long assetId);

  boolean existsByAssetIdAndUrl(Long assetId, String url);

  Optional<AssetContent> findByIdAndAsset_Id(Long id, Long assetId);

  List<AssetContent> findAllByNewsId(Long newsId);

  @Query("select content.asset.id from AssetContent content where content.news.id = :newsId")
  List<Long> findAssetIdsByNewsId(@Param("newsId") Long newsId);

  void deleteAllByNewsId(Long newsId);
}
