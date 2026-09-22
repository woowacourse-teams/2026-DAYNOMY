package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.publicdata.PublicDataListedStockClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataListedStockItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataListedStockResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockMasterSyncServiceTest {

  @Mock private PublicDataListedStockClient listedStockClient;
  @Mock private StockMasterPersistenceService persistenceService;
  @InjectMocks private StockMasterSyncService syncService;

  @Test
  @DisplayName("가장 최근 데이터가 있는 기준일의 KOSPI·KOSDAQ 보통주를 전체 페이지에서 동기화한다")
  void synchronizeLatestListedStocks() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate baseDate = today.minusDays(3);
    given(listedStockClient.getListedStocks(today, 1, 1000)).willReturn(response(0, List.of()));
    given(listedStockClient.getListedStocks(today.minusDays(1), 1, 1000))
        .willReturn(response(0, List.of()));
    given(listedStockClient.getListedStocks(today.minusDays(2), 1, 1000))
        .willReturn(response(0, List.of()));
    given(listedStockClient.getListedStocks(baseDate, 1, 1000))
        .willReturn(
            response(
                1001,
                fullPage(
                    baseDate,
                    List.of(
                        item(baseDate, "A005930", "삼성전자", "KOSPI", "KR7005930003"),
                        item(baseDate, "005935", "삼성전자우", "KOSPI", "KR7005931001"),
                        item(baseDate, "000001", "테스트스팩1호", "KOSDAQ", "KR7000000010")))));
    given(listedStockClient.getListedStocks(baseDate, 2, 1000))
        .willReturn(
            response(1001, List.of(item(baseDate, "000660", "SK하이닉스", "KOSPI", "KR7000660001"))));
    given(
            persistenceService.synchronize(
                org.mockito.ArgumentMatchers.eq(baseDate), org.mockito.ArgumentMatchers.anyList()))
        .willReturn(new StockSyncResult(baseDate, 2, 2, 0, 0));

    StockSyncResult result = syncService.synchronize(today);

    assertThat(result.syncedCount()).isEqualTo(2);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockMasterEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
    then(persistenceService)
        .should()
        .synchronize(org.mockito.ArgumentMatchers.eq(baseDate), entriesCaptor.capture());
    assertThat(entriesCaptor.getValue())
        .extracting(
            StockMasterEntry::code,
            StockMasterEntry::name,
            StockMasterEntry::market,
            StockMasterEntry::isinCode)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "005930", "삼성전자", StockMarket.KOSPI, "KR7005930003"),
            org.assertj.core.groups.Tuple.tuple(
                "000660", "SK하이닉스", StockMarket.KOSPI, "KR7000660001"));
  }

  @Test
  @DisplayName("응답 항목 수가 전체 건수보다 적으면 불완전한 종목 스냅샷을 저장하지 않는다")
  void synchronizeRejectsIncompleteSnapshot() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    given(listedStockClient.getListedStocks(today, 1, 1000))
        .willReturn(response(2, List.of(item(today, "005930", "삼성전자", "KOSPI", "KR7005930003"))));

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_MASTER_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("기준일 형식이 잘못됐거나 요청일과 다른 종목은 제외한다")
  void synchronizeSkipsStocksWithInvalidBaseDate() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    given(listedStockClient.getListedStocks(today, 1, 1000))
        .willReturn(
            response(
                3,
                List.of(
                    item(today, "005930", "삼성전자", "KOSPI", "KR7005930003"),
                    item("잘못된날짜", "000660", "SK하이닉스", "KOSPI", "KR7000660001"),
                    item(today.minusDays(1), "247540", "에코프로비엠", "KOSDAQ", "KR7247540008"))));
    given(
            persistenceService.synchronize(
                org.mockito.ArgumentMatchers.eq(today), org.mockito.ArgumentMatchers.anyList()))
        .willReturn(new StockSyncResult(today, 1, 1, 0, 0));

    syncService.synchronize(today);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockMasterEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
    then(persistenceService)
        .should()
        .synchronize(org.mockito.ArgumentMatchers.eq(today), entriesCaptor.capture());
    assertThat(entriesCaptor.getValue())
        .extracting(StockMasterEntry::code)
        .containsExactly("005930");
  }

  @Test
  @DisplayName("최근 10일 동안 종목 데이터가 없으면 동기화를 중단한다")
  void synchronizeThrowsWhenSnapshotIsMissing() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    for (int daysAgo = 0; daysAgo <= 10; daysAgo++) {
      given(listedStockClient.getListedStocks(today.minusDays(daysAgo), 1, 1000))
          .willReturn(response(0, List.of()));
    }

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_MASTER_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
  }

  private PublicDataListedStockResponse response(
      int totalCount, List<PublicDataListedStockItem> items) {
    return new PublicDataListedStockResponse(
        new PublicDataListedStockResponse.Response(
            new PublicDataListedStockResponse.Header("00", "NORMAL SERVICE."),
            new PublicDataListedStockResponse.Body(
                1000, 1, totalCount, new PublicDataListedStockResponse.Items(items))));
  }

  private PublicDataListedStockItem item(
      LocalDate baseDate, String code, String name, String market, String isinCode) {
    return item(
        baseDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
        code,
        name,
        market,
        isinCode);
  }

  private PublicDataListedStockItem item(
      String baseDate, String code, String name, String market, String isinCode) {
    return new PublicDataListedStockItem(
        baseDate, code, isinCode, market, name, "1101110000000", name);
  }

  private List<PublicDataListedStockItem> fullPage(
      LocalDate baseDate, List<PublicDataListedStockItem> leadingItems) {
    List<PublicDataListedStockItem> items = new ArrayList<>(leadingItems);
    PublicDataListedStockItem excludedItem =
        item(baseDate, "000002", "코넥스종목", "KONEX", "KR7000000028");
    while (items.size() < 1000) {
      items.add(excludedItem);
    }
    return items;
  }
}
