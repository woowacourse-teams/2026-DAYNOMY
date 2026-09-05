package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetRankingSyncServiceTest {

  @Mock private PublicDataStockPriceClient publicDataStockPriceClient;

  @Mock private AssetRankingPersistenceService assetRankingPersistenceService;

  @InjectMocks private AssetRankingSyncService assetRankingSyncService;

  @Test
  @DisplayName("코스닥 주식시세를 시가총액 기준으로 정렬해 저장한다")
  void syncKosdaqTopRankingsSavesSortedKosdaqRankings() {
    LocalDate today = LocalDate.of(2026, 8, 21);
    given(publicDataStockPriceClient.getKosdaqStockPrices(today))
        .willReturn(
            response(
                item("20260821", "000003", "낮은시총", "KOSDAQ", "100"),
                item("20260821", "000001", "높은시총", "KOSDAQ", "300"),
                item("20260821", "000002", "중간시총", "KOSDAQ", "200"),
                item("20260821", "000004", "코스피종목", "KOSPI", "1000")));
    given(assetRankingPersistenceService.saveRankings(anyList())).willReturn(3);

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    assertThat(count).isEqualTo(3);
    verify(assetRankingPersistenceService)
        .saveRankings(
            argThat(
                rankingItems ->
                    rankingItems.size() == 3
                        && rankingItems.get(0).srtnCd().equals("000001")
                        && rankingItems.get(1).srtnCd().equals("000002")
                        && rankingItems.get(2).srtnCd().equals("000003")));
  }

  @Test
  @DisplayName("오늘 데이터가 없으면 이전 날짜를 조회해 저장한다")
  void syncKosdaqTopRankingsLooksBackUntilDataExists() {
    LocalDate today = LocalDate.of(2026, 8, 23);
    LocalDate previousDate = LocalDate.of(2026, 8, 22);
    given(publicDataStockPriceClient.getKosdaqStockPrices(today)).willReturn(response());
    given(publicDataStockPriceClient.getKosdaqStockPrices(previousDate))
        .willReturn(response(item("20260822", "000001", "종목", "KOSDAQ", "300")));
    given(assetRankingPersistenceService.saveRankings(anyList())).willReturn(1);

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    assertThat(count).isEqualTo(1);
    verify(publicDataStockPriceClient).getKosdaqStockPrices(today);
    verify(publicDataStockPriceClient).getKosdaqStockPrices(previousDate);
  }

  @Test
  @DisplayName("응답 데이터가 있어도 저장 가능한 랭킹이 없으면 이전 날짜를 계속 조회한다")
  void syncKosdaqTopRankingsLooksBackWhenRankingItemsAreEmpty() {
    LocalDate today = LocalDate.of(2026, 8, 23);
    LocalDate previousDate = LocalDate.of(2026, 8, 22);
    given(publicDataStockPriceClient.getKosdaqStockPrices(today))
        .willReturn(
            response(
                item("20260823", "000001", "코스피종목", "KOSPI", "300"),
                item("20260823", "000002", "시총없는종목", "KOSDAQ", "0")));
    given(publicDataStockPriceClient.getKosdaqStockPrices(previousDate))
        .willReturn(response(item("20260822", "000003", "코스닥종목", "KOSDAQ", "300")));
    given(assetRankingPersistenceService.saveRankings(anyList())).willReturn(1);

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    assertThat(count).isEqualTo(1);
    verify(publicDataStockPriceClient).getKosdaqStockPrices(today);
    verify(publicDataStockPriceClient).getKosdaqStockPrices(previousDate);
  }

  @Test
  @DisplayName("조회 기간 내 저장 가능한 랭킹이 없으면 저장하지 않는다")
  void syncKosdaqTopRankingsDoesNotSaveWhenRankingItemsAreMissing() {
    LocalDate today = LocalDate.of(2026, 8, 23);
    for (int daysAgo = 0; daysAgo < 10; daysAgo++) {
      given(publicDataStockPriceClient.getKosdaqStockPrices(today.minusDays(daysAgo)))
          .willReturn(response(item("20260823", "000001", "코스피종목", "KOSPI", "300")));
    }

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    assertThat(count).isZero();
    verify(assetRankingPersistenceService, never()).saveRankings(anyList());
  }

  private PublicDataStockPriceResponse response(PublicDataStockPriceItem... items) {
    return new PublicDataStockPriceResponse(
        new PublicDataStockPriceResponse.Response(
            new PublicDataStockPriceResponse.Header("00", "NORMAL SERVICE."),
            new PublicDataStockPriceResponse.Body(
                items.length,
                1,
                items.length,
                new PublicDataStockPriceResponse.Items(List.of(items)))));
  }

  private PublicDataStockPriceItem item(
      String baseDate, String code, String name, String market, String marketCap) {
    return new PublicDataStockPriceItem(baseDate, code, name, market, "0", marketCap);
  }
}
