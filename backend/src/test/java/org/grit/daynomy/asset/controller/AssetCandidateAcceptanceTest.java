package org.grit.daynomy.asset.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AssetCandidateAcceptanceTest {

  @LocalServerPort private int port;

  @Autowired private AssetRepository assetRepository;

  @Autowired private AssetRankingHistoryRepository assetRankingHistoryRepository;

  @BeforeEach
  void setUp() {
    assetRankingHistoryRepository.deleteAll();
    assetRepository.deleteAll();
  }

  @Test
  @DisplayName("코스닥 대표 종목 순위 조회 API는 최신 기준일의 랭킹 페이지를 반환한다")
  void getKosdaqTopRankingsReturnsLatestRankingPage() {
    LocalDate oldDate = LocalDate.of(2026, 8, 20);
    LocalDate latestDate = LocalDate.of(2026, 8, 21);
    Asset oldAsset = assetRepository.save(new Asset("이전종목", AssetCategory.STOCK, "000001"));
    Asset firstAsset = assetRepository.save(new Asset("일위종목", AssetCategory.STOCK, "000002"));
    Asset secondAsset = assetRepository.save(new Asset("이위종목", AssetCategory.STOCK, "000003"));
    assetRankingHistoryRepository.save(new AssetRankingHistory(oldAsset, 1, oldDate));
    assetRankingHistoryRepository.save(new AssetRankingHistory(secondAsset, 2, latestDate));
    assetRankingHistoryRepository.save(new AssetRankingHistory(firstAsset, 1, latestDate));

    given()
        .baseUri("http://localhost")
        .port(port)
        .queryParam("page", 1)
        .queryParam("size", 1)
        .when()
        .get("/api/assets/kosdaq/top")
        .then()
        .statusCode(200)
        .body("baseDate", equalTo("2026-08-21"))
        .body("rankings", hasSize(1))
        .body("rankings[0].rank", equalTo(1))
        .body("rankings[0].code", equalTo("000002"))
        .body("rankings[0].name", equalTo("일위종목"))
        .body("page", equalTo(1))
        .body("size", equalTo(1))
        .body("totalPages", equalTo(2))
        .body("totalElements", equalTo(2))
        .body("hasNext", equalTo(true));
  }
}
