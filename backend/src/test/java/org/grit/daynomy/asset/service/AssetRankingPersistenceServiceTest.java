package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetRankingPersistenceServiceTest {

  @Mock private AssetRepository assetRepository;

  @Mock private AssetRankingHistoryRepository assetRankingHistoryRepository;

  @InjectMocks private AssetRankingPersistenceService assetRankingPersistenceService;

  @Test
  @DisplayName("랭킹 후보를 기존 기준일 이력과 교체해 저장한다")
  void saveRankingsReplacesRankingsByBaseDate() {
    Asset firstAsset = new Asset("일위종목", AssetCategory.STOCK, "000001");
    Asset secondAsset = new Asset("이위종목", AssetCategory.STOCK, "000002");
    given(assetRepository.findByCategoryAndAssetCode(AssetCategory.STOCK, "000001"))
        .willReturn(Optional.of(firstAsset));
    given(assetRepository.findByCategoryAndAssetCode(AssetCategory.STOCK, "000002"))
        .willReturn(Optional.of(secondAsset));

    int count =
        assetRankingPersistenceService.saveRankings(
            List.of(
                item("20260821", "000001", "일위종목", "KOSDAQ", "300"),
                item("20260821", "000002", "이위종목", "KOSDAQ", "200")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<AssetRankingHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
    assertThat(count).isEqualTo(2);
    verify(assetRankingHistoryRepository).deleteByRankedDate(LocalDate.of(2026, 8, 21));
    verify(assetRankingHistoryRepository).flush();
    verify(assetRankingHistoryRepository).saveAll(historiesCaptor.capture());
    assertThat(historiesCaptor.getValue())
        .extracting(AssetRankingHistory::getRanking)
        .containsExactly(1, 2);
  }

  @Test
  @DisplayName("기존 자산이 없으면 새 자산을 생성해 랭킹에 연결한다")
  void saveRankingsCreatesAssetWhenMissing() {
    given(assetRepository.findByCategoryAndAssetCode(AssetCategory.STOCK, "000001"))
        .willReturn(Optional.empty());
    given(assetRepository.save(any(Asset.class)))
        .willReturn(new Asset("신규종목", AssetCategory.STOCK, "000001"));

    int count =
        assetRankingPersistenceService.saveRankings(
            List.of(item("20260821", "000001", "신규종목", "KOSDAQ", "300")));

    assertThat(count).isEqualTo(1);
    verify(assetRepository).save(any(Asset.class));
    verify(assetRankingHistoryRepository).saveAll(any());
  }

  @Test
  @DisplayName("랭킹 후보가 없으면 저장하지 않는다")
  void saveRankingsDoesNothingWhenEmpty() {
    int count = assetRankingPersistenceService.saveRankings(List.of());

    assertThat(count).isZero();
    verify(assetRankingHistoryRepository, never()).deleteByRankedDate(any());
    verify(assetRankingHistoryRepository, never()).saveAll(any());
  }

  private PublicDataStockPriceItem item(
      String baseDate, String code, String name, String market, String marketCap) {
    return new PublicDataStockPriceItem(baseDate, code, name, market, "0", marketCap);
  }
}
