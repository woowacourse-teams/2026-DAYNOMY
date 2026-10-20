package org.grit.daynomy.asset.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.dto.StockSearchResponse;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StockSearchService {

  private static final int SEARCH_RESULT_LIMIT = 20;
  private static final Set<AssetCategory> SEARCH_CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);

  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public StockSearchResponse search(String keyword) {
    return search(keyword, "KR");
  }

  @Transactional(readOnly = true)
  public StockSearchResponse search(String keyword, String country) {
    if (!Set.of("KR", "US").contains(country)) {
      throw new IllegalArgumentException("지원하지 않는 국가입니다.");
    }
    Set<StockMarket> markets =
        "US".equals(country) ? StockMarket.usMarkets() : StockMarket.domesticMarkets();
    String normalizedKeyword = escapeLikeKeyword(keyword.strip());
    return StockSearchResponse.from(
        assetRepository.searchListedSecurities(
            normalizedKeyword, SEARCH_CATEGORIES, markets, PageRequest.of(0, SEARCH_RESULT_LIMIT)));
  }

  private String escapeLikeKeyword(String keyword) {
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
