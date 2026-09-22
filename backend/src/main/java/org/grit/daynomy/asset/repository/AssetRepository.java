package org.grit.daynomy.asset.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

  Optional<Asset> findByCategoryAndAssetCode(AssetCategory category, String assetCode);

  List<Asset> findAllByCategory(AssetCategory category);

  @Query(
      """
      SELECT a
      FROM Asset a
      WHERE a.category = :category
        AND a.listed = true
        AND (
          LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(a.assetCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
        )
      ORDER BY
        CASE
          WHEN LOWER(a.assetCode) = LOWER(:keyword) THEN 0
          WHEN LOWER(a.name) = LOWER(:keyword) THEN 1
          WHEN LOWER(a.assetCode) LIKE LOWER(CONCAT(:keyword, '%')) ESCAPE '!' THEN 2
          WHEN LOWER(a.name) LIKE LOWER(CONCAT(:keyword, '%')) ESCAPE '!' THEN 3
          ELSE 4
        END,
        a.name ASC,
        a.id ASC
      """)
  List<Asset> searchListedStocks(
      @Param("keyword") String keyword,
      @Param("category") AssetCategory category,
      Pageable pageable);
}
