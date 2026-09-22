package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.dto.StockSearchResponse;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StockSearchService {

  private static final int SEARCH_RESULT_LIMIT = 20;

  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public StockSearchResponse search(String keyword) {
    String normalizedKeyword = escapeLikeKeyword(keyword.strip());
    return StockSearchResponse.from(
        assetRepository.searchListedStocks(
            normalizedKeyword, AssetCategory.STOCK, PageRequest.of(0, SEARCH_RESULT_LIMIT)));
  }

  private String escapeLikeKeyword(String keyword) {
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
