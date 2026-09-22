package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {

  @Mock private StockDailyPriceRepository stockDailyPriceRepository;
  @InjectMocks private StockPriceService stockPriceService;

  @Test
  @DisplayName("종목의 가장 최근 종가를 반환한다")
  void getLatestPrice() {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(1L);
    given(asset.getAssetCode()).willReturn("005930");
    given(asset.getName()).willReturn("삼성전자");
    StockDailyPrice price =
        new StockDailyPrice(asset, LocalDate.of(2026, 9, 18), new BigDecimal("82000.00"));
    given(stockDailyPriceRepository.findFirstByAssetIdOrderByBaseDateDesc(1L))
        .willReturn(Optional.of(price));

    var response = stockPriceService.getLatestPrice(1L);

    assertThat(response.assetCode()).isEqualTo("005930");
    assertThat(response.closePrice()).isEqualByComparingTo("82000.00");
  }

  @Test
  @DisplayName("저장된 종가가 없으면 찾을 수 없음 예외를 반환한다")
  void getLatestPriceThrowsWhenMissing() {
    given(stockDailyPriceRepository.findFirstByAssetIdOrderByBaseDateDesc(1L))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> stockPriceService.getLatestPrice(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_NOT_FOUND);
  }
}
