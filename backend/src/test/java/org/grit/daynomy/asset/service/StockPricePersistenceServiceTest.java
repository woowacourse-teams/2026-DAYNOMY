package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockPricePersistenceServiceTest {

  @Mock private AssetRepository assetRepository;
  @Mock private StockDailyPriceRepository stockDailyPriceRepository;
  @InjectMocks private StockPricePersistenceService persistenceService;

  @Test
  @DisplayName("상장 종목의 종가는 신규 저장하고 기존 종가는 갱신한다")
  void synchronizeStockPrices() {
    LocalDate baseDate = LocalDate.of(2026, 9, 18);
    Asset samsung = stock(1L, "005930", true);
    Asset skHynix = stock(2L, "000660", true);
    StockDailyPrice existingPrice = new StockDailyPrice(samsung, baseDate, new BigDecimal("81000"));
    given(assetRepository.findAllByCategory(AssetCategory.STOCK))
        .willReturn(List.of(samsung, skHynix));
    given(stockDailyPriceRepository.findAllByBaseDate(baseDate)).willReturn(List.of(existingPrice));

    StockPriceSyncResult result =
        persistenceService.synchronize(
            baseDate,
            List.of(
                new StockPriceEntry("005930", baseDate, new BigDecimal("82000")),
                new StockPriceEntry("000660", baseDate, new BigDecimal("190000")),
                new StockPriceEntry("999999", baseDate, new BigDecimal("1000"))));

    assertThat(result).isEqualTo(new StockPriceSyncResult(baseDate, 3, 1, 1, 1));
    assertThat(existingPrice.getClosePrice()).isEqualByComparingTo("82000");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockDailyPrice>> pricesCaptor = ArgumentCaptor.forClass(List.class);
    then(stockDailyPriceRepository).should().saveAll(pricesCaptor.capture());
    assertThat(pricesCaptor.getValue())
        .hasSize(2)
        .extracting(price -> price.getAsset().getAssetCode())
        .containsExactly("005930", "000660");
  }

  private Asset stock(Long id, String code, boolean listed) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    given(asset.getAssetCode()).willReturn(code);
    given(asset.isListed()).willReturn(listed);
    return asset;
  }
}
