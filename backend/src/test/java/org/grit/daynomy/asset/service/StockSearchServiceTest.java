package org.grit.daynomy.asset.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class StockSearchServiceTest {

  @Mock private AssetRepository assetRepository;
  @InjectMocks private StockSearchService stockSearchService;

  @Test
  @DisplayName("국내 주식 검색은 검색어를 정규화하고 결과를 20개로 제한한다")
  void searchNormalizesKeywordAndLimitsResults() {
    PageRequest limit = PageRequest.of(0, 20);
    given(assetRepository.searchListedStocks("삼성!%", AssetCategory.STOCK, limit))
        .willReturn(List.of());

    stockSearchService.search("  삼성%  ");

    then(assetRepository).should().searchListedStocks("삼성!%", AssetCategory.STOCK, limit);
  }
}
