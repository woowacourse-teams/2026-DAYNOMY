package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockPriceSyncServiceTest {

  @Mock private PublicDataStockPriceClient stockPriceClient;
  @Mock private StockPricePersistenceService persistenceService;
  @InjectMocks private StockPriceSyncService syncService;

  @Test
  @DisplayName("가장 최근 거래일의 KOSPI·KOSDAQ 종가를 전체 페이지에서 동기화한다")
  void synchronizeLatestStockPrices() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate baseDate = today.minusDays(3);
    stubEmptyDay(today);
    stubEmptyDay(today.minusDays(1));
    stubEmptyDay(today.minusDays(2));
    given(stockPriceClient.getStockPrices(baseDate, StockMarket.KOSPI, 1, 1000))
        .willReturn(
            response(
                1001,
                fullPage(
                    baseDate,
                    List.of(item(baseDate, "005930", "82000"), item(baseDate, "잘못된코드", "1000")))));
    given(stockPriceClient.getStockPrices(baseDate, StockMarket.KOSPI, 2, 1000))
        .willReturn(response(1001, List.of(item(baseDate, "000660", "190000"))));
    given(stockPriceClient.getStockPrices(baseDate, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(baseDate, "247540", "285000"))));
    given(
            persistenceService.synchronize(
                org.mockito.ArgumentMatchers.eq(baseDate), org.mockito.ArgumentMatchers.anyList()))
        .willReturn(new StockPriceSyncResult(baseDate, 3, 3, 0, 0));

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.receivedCount()).isEqualTo(3);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockPriceEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
    then(persistenceService)
        .should()
        .synchronize(org.mockito.ArgumentMatchers.eq(baseDate), entriesCaptor.capture());
    assertThat(entriesCaptor.getValue())
        .extracting(StockPriceEntry::assetCode, StockPriceEntry::closePrice)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("005930", new java.math.BigDecimal("82000")),
            org.assertj.core.groups.Tuple.tuple("000660", new java.math.BigDecimal("190000")),
            org.assertj.core.groups.Tuple.tuple("247540", new java.math.BigDecimal("285000")));
  }

  @Test
  @DisplayName("시장별 응답 항목 수가 전체 건수보다 적으면 불완전한 종가 스냅샷을 저장하지 않는다")
  void synchronizeRejectsIncompleteMarketSnapshot() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(2, List.of(item(today, "005930", "82000"))));

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("최근 10일 동안 양 시장의 종가 데이터가 없으면 동기화를 중단한다")
  void synchronizeThrowsWhenSnapshotIsMissing() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    for (int daysAgo = 0; daysAgo <= 10; daysAgo++) {
      stubEmptyDay(today.minusDays(daysAgo));
    }

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
  }

  private void stubEmptyDay(LocalDate date) {
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(0, List.of()));
  }

  private PublicDataStockPriceResponse response(
      int totalCount, List<PublicDataStockPriceItem> items) {
    return new PublicDataStockPriceResponse(
        new PublicDataStockPriceResponse.Response(
            new PublicDataStockPriceResponse.Header("00", "NORMAL SERVICE."),
            new PublicDataStockPriceResponse.Body(
                1000, 1, totalCount, new PublicDataStockPriceResponse.Items(items))));
  }

  private PublicDataStockPriceItem item(LocalDate baseDate, String code, String closePrice) {
    return new PublicDataStockPriceItem(
        baseDate.format(DateTimeFormatter.BASIC_ISO_DATE),
        code,
        "테스트종목",
        "KOSPI",
        closePrice,
        "1000000000");
  }

  private List<PublicDataStockPriceItem> fullPage(
      LocalDate baseDate, List<PublicDataStockPriceItem> leadingItems) {
    List<PublicDataStockPriceItem> items = new ArrayList<>(leadingItems);
    PublicDataStockPriceItem invalidItem = item(baseDate, "잘못된코드", "1000");
    while (items.size() < 1000) {
      items.add(invalidItem);
    }
    return items;
  }
}
