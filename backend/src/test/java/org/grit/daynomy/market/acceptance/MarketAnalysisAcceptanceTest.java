package org.grit.daynomy.market.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.*;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketAnalysisAcceptanceTest {
  @LocalServerPort int port;
  @Autowired NewsRepository news;
  @Autowired NewsMarketAnalysisRepository analyses;

  @Test
  void readsPersistedAnalysisAndHidesUnpublishedNews() {
    var published =
        news.save(
            News.createPublished(
                "시장",
                "본문",
                "image.png",
                List.of(new NewsSourceInfo("공시", "https://example.invalid/market")),
                Category.STOCK,
                Instant.parse("2026-10-01T00:00:00Z")));
    var draft =
        news.save(
            News.createDraft(
                "초안",
                "본문",
                "image.png",
                List.of(new NewsSourceInfo("공시", "https://example.invalid/draft")),
                Category.STOCK));
    analyses.save(new NewsMarketAnalysis(published, "물가 상승 위험이에요.", "다음 물가 발표를 확인하세요."));
    analyses.save(new NewsMarketAnalysis(draft, "비공개 위험이에요.", "비공개 지표예요."));
    given()
        .port(port)
        .get("/api/news/{id}/market-analysis", published.getId())
        .then()
        .statusCode(200)
        .body("risk", equalTo("물가 상승 위험이에요."))
        .body("watchPoint", equalTo("다음 물가 발표를 확인하세요."));
    given()
        .port(port)
        .get("/api/news/{id}/market-analysis", draft.getId())
        .then()
        .statusCode(404)
        .body("code", equalTo("MARKET_ANALYSIS_NOT_FOUND"));
  }
}
