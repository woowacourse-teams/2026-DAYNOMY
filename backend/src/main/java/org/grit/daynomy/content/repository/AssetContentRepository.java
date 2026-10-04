package org.grit.daynomy.content.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.content.domain.AssetContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetContentRepository extends JpaRepository<AssetContent, Long> {

  List<AssetContent> findAllByAssetIdOrderByCreatedAtDescIdDesc(Long assetId);

  boolean existsByAssetIdAndUrl(Long assetId, String url);

  Optional<AssetContent> findByIdAndAsset_Id(Long id, Long assetId);
}
