package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "external.public-data.service-key=test-service-key",
      "external.public-data.stock-price-url=https://example.com"
    })
@Transactional
class AssetRankingSyncServiceTest {

  @Autowired private AssetRepository assetRepository;

  @Autowired private AssetRankingHistoryRepository assetRankingHistoryRepository;

  @Autowired private AssetRankingPersistenceService assetRankingPersistenceService;

  private PublicDataStockPriceClient publicDataStockPriceClient;
  private AssetRankingSyncService assetRankingSyncService;

  @BeforeEach
  void setUp() {
    assetRankingHistoryRepository.deleteAll();
    assetRepository.deleteAll();
    publicDataStockPriceClient = mock(PublicDataStockPriceClient.class);
    assetRankingSyncService =
        new AssetRankingSyncService(publicDataStockPriceClient, assetRankingPersistenceService);
  }

  @Test
  @DisplayName("코스닥 주식시세를 시가총액 기준으로 정렬해 랭킹 이력을 저장한다")
  void syncKosdaqTopRankingsSavesRankingHistories() {
    LocalDate today = LocalDate.of(2026, 8, 21);
    when(publicDataStockPriceClient.getKosdaqStockPrices(today))
        .thenReturn(
            response(
                item("20260821", "000003", "낮은시총", "KOSDAQ", "100"),
                item("20260821", "000001", "높은시총", "KOSDAQ", "300"),
                item("20260821", "000002", "중간시총", "KOSDAQ", "200"),
                item("20260821", "000004", "코스피종목", "KOSPI", "1000")));

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    List<AssetRankingHistory> histories =
        assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(today);

    assertThat(count).isEqualTo(3);
    assertThat(histories).hasSize(3);
    assertThat(histories.get(0).getRanking()).isEqualTo(1);
    assertThat(histories.get(0).getAsset().getAssetCode()).isEqualTo("000001");
    assertThat(histories.get(1).getAsset().getAssetCode()).isEqualTo("000002");
    assertThat(histories.get(2).getAsset().getAssetCode()).isEqualTo("000003");
  }

  @Test
  @DisplayName("기존 자산이 있으면 새로 만들지 않고 그대로 랭킹에 연결한다")
  void syncKosdaqTopRankingsReusesExistingAsset() {
    LocalDate today = LocalDate.of(2026, 8, 21);
    Asset asset = assetRepository.save(new Asset("이전이름", AssetCategory.STOCK, "000001"));
    when(publicDataStockPriceClient.getKosdaqStockPrices(today))
        .thenReturn(response(item("20260821", "000001", "변경이름", "KOSDAQ", "300")));

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    Asset savedAsset = assetRepository.findById(asset.getId()).orElseThrow();
    List<AssetRankingHistory> histories =
        assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(today);

    assertThat(count).isEqualTo(1);
    assertThat(savedAsset.getName()).isEqualTo("이전이름");
    assertThat(histories).hasSize(1);
    assertThat(histories.get(0).getAsset().getId()).isEqualTo(asset.getId());
  }

  @Test
  @DisplayName("오늘 데이터가 없으면 이전 날짜를 조회해 데이터가 있는 날짜의 랭킹을 저장한다")
  void syncKosdaqTopRankingsLooksBackUntilDataExists() {
    LocalDate today = LocalDate.of(2026, 8, 23);
    LocalDate previousDate = LocalDate.of(2026, 8, 22);
    when(publicDataStockPriceClient.getKosdaqStockPrices(today)).thenReturn(response());
    when(publicDataStockPriceClient.getKosdaqStockPrices(previousDate))
        .thenReturn(response(item("20260822", "000001", "종목", "KOSDAQ", "300")));

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    assertThat(count).isEqualTo(1);
    assertThat(assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(today)).isEmpty();
    assertThat(assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(previousDate))
        .hasSize(1);
  }

  @Test
  @DisplayName("응답 데이터가 있어도 저장 가능한 랭킹이 없으면 이전 날짜를 계속 조회한다")
  void syncKosdaqTopRankingsLooksBackWhenRankingItemsAreEmpty() {
    LocalDate today = LocalDate.of(2026, 8, 23);
    LocalDate previousDate = LocalDate.of(2026, 8, 22);
    when(publicDataStockPriceClient.getKosdaqStockPrices(today))
        .thenReturn(
            response(
                item("20260823", "000001", "코스피종목", "KOSPI", "300"),
                item("20260823", "000002", "시총없는종목", "KOSDAQ", "0")));
    when(publicDataStockPriceClient.getKosdaqStockPrices(previousDate))
        .thenReturn(response(item("20260822", "000003", "코스닥종목", "KOSDAQ", "300")));

    int count = assetRankingSyncService.syncKosdaqTopRankings(today);

    assertThat(count).isEqualTo(1);
    assertThat(assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(today)).isEmpty();
    assertThat(assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(previousDate))
        .hasSize(1);
  }

  private PublicDataStockPriceResponse response(PublicDataStockPriceItem... items) {
    return new PublicDataStockPriceResponse(
        new PublicDataStockPriceResponse.Header("00", "NORMAL SERVICE."),
        new PublicDataStockPriceResponse.Body(
            items.length, 1, items.length, new PublicDataStockPriceResponse.Items(List.of(items))));
  }

  private PublicDataStockPriceItem item(
      String baseDate, String code, String name, String market, String marketCap) {
    return new PublicDataStockPriceItem(baseDate, code, name, market, "0", marketCap);
  }
}
