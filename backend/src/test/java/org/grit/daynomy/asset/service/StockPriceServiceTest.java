package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

  @Test
  @DisplayName("여러 종목의 기간별 종가를 중복 종목 없이 반환한다")
  void getPrices() {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(1L);
    given(asset.getAssetCode()).willReturn("005930");
    given(asset.getName()).willReturn("삼성전자");
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 14);
    given(
            stockDailyPriceRepository
                .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                    List.of(1L, 2L), from, to))
        .willReturn(
            List.of(
                new StockDailyPrice(asset, LocalDate.of(2026, 9, 1), new BigDecimal("80000")),
                new StockDailyPrice(asset, LocalDate.of(2026, 9, 2), new BigDecimal("81000"))));

    var response = stockPriceService.getPrices(List.of(1L, 1L, 2L), from, to);

    assertThat(response.prices()).hasSize(2);
    then(stockDailyPriceRepository)
        .should()
        .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            List.of(1L, 2L), from, to);
  }

  @Test
  @DisplayName("기간별 종가 조회는 31일을 초과하거나 역전된 기간을 거부한다")
  void getPricesRejectsInvalidRange() {
    LocalDate from = LocalDate.of(2026, 9, 1);

    assertThatThrownBy(() -> stockPriceService.getPrices(List.of(1L), from, from.plusDays(32)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.INVALID_STOCK_PRICE_QUERY_RANGE);
    assertThatThrownBy(() -> stockPriceService.getPrices(List.of(1L), from, from.minusDays(1)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.INVALID_STOCK_PRICE_QUERY_RANGE);
  }
}
