package org.grit.daynomy.asset.repository;

import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {

  Optional<Asset> findByCategoryAndAssetCode(AssetCategory category, String assetCode);
}
