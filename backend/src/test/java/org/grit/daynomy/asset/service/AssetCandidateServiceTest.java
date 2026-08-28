package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.dto.AssetCandidateResponse;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AssetCandidateServiceTest {

  @Mock private AssetRankingHistoryRepository assetRankingHistoryRepository;

  @InjectMocks private AssetCandidateService assetCandidateService;

  @Test
  @DisplayName("코스닥 대표 종목 순위 조회는 최신 날짜의 랭킹 페이지를 반환한다")
  void getKosdaqTopRankingsReturnsLatestRankingPage() {
    LocalDate latestDate = LocalDate.of(2026, 8, 21);
    AssetRankingHistory firstRanking =
        new AssetRankingHistory(new Asset("일위종목", AssetCategory.STOCK, "000002"), 1, latestDate);
    PageRequest pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "ranking"));
    given(assetRankingHistoryRepository.findFirstByOrderByRankedDateDesc())
        .willReturn(Optional.of(firstRanking));
    given(assetRankingHistoryRepository.findAllByRankedDate(latestDate, pageable))
        .willReturn(new PageImpl<>(List.of(firstRanking), pageable, 2));

    AssetCandidatesResponse response = assetCandidateService.getKosdaqTopRankings(null, 1, 1);

    assertThat(response.baseDate()).isEqualTo("2026-08-21");
    assertThat(response.rankings()).hasSize(1);
    assertThat(response.rankings().get(0).rank()).isEqualTo(1);
    assertThat(response.rankings().get(0).code()).isEqualTo("000002");
    assertThat(response.rankings().get(0).name()).isEqualTo("일위종목");
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.totalPages()).isEqualTo(2);
    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.hasNext()).isTrue();
  }

  @Test
  @DisplayName("코스닥 대표 종목 검색은 공백과 LIKE 문자를 처리해 최신 랭킹을 조회한다")
  void getKosdaqTopRankingsSearchesLatestRankingsByEscapedKeyword() {
    LocalDate latestDate = LocalDate.of(2026, 8, 21);
    AssetRankingHistory ranking =
        new AssetRankingHistory(new Asset("에코%_!", AssetCategory.STOCK, "000002"), 1, latestDate);
    PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "ranking"));
    given(assetRankingHistoryRepository.findFirstByOrderByRankedDateDesc())
        .willReturn(Optional.of(ranking));
    given(
            assetRankingHistoryRepository.searchByRankedDateAndKeyword(
                latestDate, "에코!%!_!!", pageable))
        .willReturn(new PageImpl<>(List.of(ranking), pageable, 1));

    AssetCandidatesResponse response =
        assetCandidateService.getKosdaqTopRankings("  에코%_!  ", 1, 20);

    assertThat(response.rankings())
        .extracting(AssetCandidateResponse::name)
        .containsExactly("에코%_!");
    then(assetRankingHistoryRepository)
        .should()
        .searchByRankedDateAndKeyword(latestDate, "에코!%!_!!", pageable);
  }

  @Test
  @DisplayName("저장된 랭킹이 없으면 빈 순위 목록을 반환한다")
  void getKosdaqTopRankingsReturnsEmptyRankingsWhenMissing() {
    given(assetRankingHistoryRepository.findFirstByOrderByRankedDateDesc())
        .willReturn(Optional.empty());

    AssetCandidatesResponse response = assetCandidateService.getKosdaqTopRankings(null, 1, 20);

    assertThat(response.baseDate()).isNull();
    assertThat(response.rankings()).isEmpty();
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.totalPages()).isZero();
    assertThat(response.totalElements()).isZero();
    assertThat(response.hasNext()).isFalse();
  }
}
