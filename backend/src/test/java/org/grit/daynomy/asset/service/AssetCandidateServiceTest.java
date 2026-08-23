package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "external.public-data.service-key=test-service-key",
      "external.public-data.stock-price-url=https://example.com"
    })
class AssetCandidateServiceTest {

  @Autowired private AssetCandidateService assetCandidateService;

  @Autowired private AssetRepository assetRepository;

  @Autowired private AssetRankingHistoryRepository assetRankingHistoryRepository;

  @BeforeEach
  void setUp() {
    assetRankingHistoryRepository.deleteAll();
    assetRepository.deleteAll();
  }

  @Test
  @DisplayName("코스닥 대표 종목 순위 조회는 최신 날짜의 랭킹을 반환한다")
  void getKosdaqTopRankingsReturnsLatestRankings() {
    LocalDate oldDate = LocalDate.of(2026, 8, 20);
    LocalDate latestDate = LocalDate.of(2026, 8, 21);
    Asset oldAsset = assetRepository.save(new Asset("이전종목", AssetCategory.STOCK, "000001"));
    Asset firstAsset = assetRepository.save(new Asset("일위종목", AssetCategory.STOCK, "000002"));
    Asset secondAsset = assetRepository.save(new Asset("이위종목", AssetCategory.STOCK, "000003"));
    assetRankingHistoryRepository.save(new AssetRankingHistory(oldAsset, 1, oldDate));
    assetRankingHistoryRepository.save(new AssetRankingHistory(secondAsset, 2, latestDate));
    assetRankingHistoryRepository.save(new AssetRankingHistory(firstAsset, 1, latestDate));

    AssetCandidatesResponse response = assetCandidateService.getKosdaqTopRankings();

    assertThat(response.baseDate()).isEqualTo("2026-08-21");
    assertThat(response.rankings()).hasSize(2);
    assertThat(response.rankings().get(0).rank()).isEqualTo(1);
    assertThat(response.rankings().get(0).code()).isEqualTo("000002");
    assertThat(response.rankings().get(0).name()).isEqualTo("일위종목");
    assertThat(response.rankings().get(1).rank()).isEqualTo(2);
    assertThat(response.rankings().get(1).code()).isEqualTo("000003");
  }

  @Test
  @DisplayName("저장된 랭킹이 없으면 빈 순위 목록을 반환한다")
  void getKosdaqTopRankingsReturnsEmptyRankingsWhenMissing() {
    AssetCandidatesResponse response = assetCandidateService.getKosdaqTopRankings();

    assertThat(response.baseDate()).isNull();
    assertThat(response.rankings()).isEmpty();
  }
}
