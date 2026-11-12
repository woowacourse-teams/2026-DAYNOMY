package org.grit.daynomy.asset.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

  Optional<Asset> findByCategoryAndAssetCode(AssetCategory category, String assetCode);

  List<Asset> findAllByCategory(AssetCategory category);

  List<Asset> findAllByCategoryIn(Set<AssetCategory> categories);

  List<Asset> findAllByCategoryInAndListedTrue(Set<AssetCategory> categories);

  Page<Asset> findAllByIndustries_IdAndCategoryInAndListedTrueOrderByNameAscIdAsc(
      Long industryId, Set<AssetCategory> categories, Pageable pageable);

  @Query(
      """
      SELECT DISTINCT asset
      FROM Asset asset
      JOIN asset.industries industry
      WHERE asset.category IN :categories
        AND asset.listed = true
        AND industry.id IN :industryIds
      ORDER BY asset.id
      """)
  List<Asset> findListedSecuritiesByIndustryIds(
      @Param("industryIds") Set<Long> industryIds,
      @Param("categories") Set<AssetCategory> categories);

  @Query(
      """
      SELECT DISTINCT asset
      FROM Asset asset
      JOIN asset.industries industry
      WHERE asset.category = org.grit.daynomy.asset.domain.AssetCategory.STOCK
        AND asset.listed = true
        AND industry.active = true
        AND industry.code IN :industryCodes
      ORDER BY asset.id
      """)
  List<Asset> findListedStocksByIndustryCodes(@Param("industryCodes") Set<String> industryCodes);

  @Query(
      """
      SELECT a
      FROM Asset a
      WHERE a.category IN :categories
        AND a.listed = true
        AND a.market IN :markets
        AND (
          LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(a.assetCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
        )
      ORDER BY
        CASE
          WHEN LOWER(a.assetCode) = LOWER(:keyword) THEN 0
          WHEN LOWER(a.name) = LOWER(:keyword) OR LOWER(a.nameKo) = LOWER(:keyword) THEN 1
          WHEN LOWER(a.assetCode) LIKE LOWER(CONCAT(:keyword, '%')) ESCAPE '!' THEN 2
          WHEN LOWER(a.name) LIKE LOWER(CONCAT(:keyword, '%')) ESCAPE '!'
            OR LOWER(a.nameKo) LIKE LOWER(CONCAT(:keyword, '%')) ESCAPE '!' THEN 3
          ELSE 4
        END,
        COALESCE(NULLIF(a.nameKo, ''), a.name) ASC,
        a.id ASC
      """)
  List<Asset> searchListedSecurities(
      @Param("keyword") String keyword,
      @Param("categories") Set<AssetCategory> categories,
      @Param("markets") Set<StockMarket> markets,
      Pageable pageable);
}
