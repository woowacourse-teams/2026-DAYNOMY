package org.grit.daynomy.investmentcalendar.repository;

import java.util.List;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InvestmentCalendarHoldingAssetRepository
    extends Repository<PortfolioHolding, Long> {

  @Query(
      "select distinct holding.asset from PortfolioHolding holding "
          + "where holding.asset.listed = true and holding.asset.category in :categories")
  List<Asset> findDistinctListedAssetsByCategories(
      @Param("categories") Set<AssetCategory> categories);
}
