package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisEvidence;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPersonalContext;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortfolioAnalysisShareAcceptanceTest extends SharedDatabaseTest {

  private static final String OWNER_API = "/api/users/me/portfolio/analysis/shares";
  private static final String PUBLIC_API = "/api/portfolio/analysis/shares";
  private static final String SHARE_TOKEN_HEADER = "X-Portfolio-Share-Token";

  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired PortfolioRepository portfolios;
  @Autowired PortfolioAnalysisRepository analyses;
  @Autowired PortfolioAnalysisEvidenceRepository evidence;
  @Autowired PortfolioHoldingRepository holdings;
  @Autowired PortfolioHoldingHistoryRepository holdingHistories;
  @Autowired AssetRepository assets;
  @Autowired StockDailyPriceRepository prices;
  @Autowired JwtTokenProvider tokens;

  @Test
  void sharesOnlyThePublicSnapshotAndSupportsMultipleCommentsWithOneReplyLevel() {
    Member owner = member("작성자");
    Member reviewer = member("리뷰어");
    Member outsider = member("다른 리뷰어");
    PortfolioAnalysis analysis = analysis(owner);

    var created =
        client(owner)
            .body(
                Map.of(
                    "analysisId",
                    analysis.getId(),
                    "question",
                    "반도체 비중이 너무 높은지 의견을 받고 싶어요.",
                    "assetNames",
                    List.of("삼성전자")))
            .post(OWNER_API)
            .then()
            .statusCode(200)
            .extract();
    String token = created.path("token");
    int shareId = created.path("share.id");

    Map<String, Object> publicAnalysis =
        given()
            .port(port)
            .header(SHARE_TOKEN_HEADER, token)
            .get(PUBLIC_API)
            .then()
            .statusCode(200)
            .header("Referrer-Policy", "no-referrer")
            .header("X-Robots-Tag", "noindex, nofollow")
            .body("ownerNickname", equalTo("작성자"))
            .body("analysis.allAssetsShared", equalTo(false))
            .body("analysis.hasAnalysis", equalTo(true))
            .body("analysis.overallImpact", equalTo(""))
            .body("analysis.impacts", hasSize(1))
            .body("analysis.impacts[0].assetName", equalTo("삼성전자"))
            .body("analysis.impacts[0].weight", equalTo(60))
            .extract()
            .path("analysis");
    assertThat(publicAnalysis)
        .doesNotContainKeys("personalContext", "priceContexts", "quantity", "purchaseAmount");
    assertThat(publicAnalysis.toString())
        .doesNotContain(
            "SK하이닉스", "averagePurchasePrice", "evaluationAmount", "profitLoss", "returnRate");

    int firstCommentId =
        client(reviewer)
            .body(Map.of("content", "반도체 비중을 조금 줄이는 방향도 함께 검토해 보세요."))
            .header(SHARE_TOKEN_HEADER, token)
            .post(PUBLIC_API + "/comments")
            .then()
            .statusCode(200)
            .extract()
            .path("id");
    int commentId =
        client(reviewer)
            .body(Map.of("content", "반도체 비중은 유지하되 다른 업종을 추가하는 방법도 있어요."))
            .header(SHARE_TOKEN_HEADER, token)
            .post(PUBLIC_API + "/comments")
            .then()
            .statusCode(200)
            .extract()
            .path("id");

    client(reviewer)
        .header(SHARE_TOKEN_HEADER, token)
        .get(PUBLIC_API)
        .then()
        .statusCode(200)
        .body("myComments", hasSize(2))
        .body("ownerShare", nullValue());
    client(owner)
        .header(SHARE_TOKEN_HEADER, token)
        .get(PUBLIC_API)
        .then()
        .statusCode(200)
        .body("canComment", equalTo(false))
        .body("ownerShare.id", equalTo(shareId))
        .body("ownerShare.comments", hasSize(2));
    client(outsider)
        .header(SHARE_TOKEN_HEADER, token)
        .get(PUBLIC_API)
        .then()
        .statusCode(200)
        .body("ownerShare", nullValue())
        .body("myComments", hasSize(0));
    given()
        .port(port)
        .header(SHARE_TOKEN_HEADER, token)
        .get(PUBLIC_API)
        .then()
        .statusCode(200)
        .body("ownerShare", nullValue())
        .body("myComments", hasSize(0));
    client(outsider)
        .body(Map.of("content", "다른 사람의 댓글에는 대댓글을 작성할 수 없어요."))
        .header(SHARE_TOKEN_HEADER, token)
        .post(PUBLIC_API + "/comments/" + commentId + "/replies")
        .then()
        .statusCode(404)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND"));

    client(reviewer)
        .body(Map.of("content", "반도체 비중을 조금씩 줄이는 방향으로 검토해 보세요."))
        .header(SHARE_TOKEN_HEADER, token)
        .put(PUBLIC_API + "/comments/" + firstCommentId)
        .then()
        .statusCode(200);

    client(owner)
        .body(Map.of("content", "작성자 답변입니다. 다른 업종 추가도 함께 검토하고 있어요."))
        .post(OWNER_API + "/" + shareId + "/comments/" + commentId + "/replies")
        .then()
        .statusCode(200);
    int reviewerReplyId =
        client(reviewer)
            .body(Map.of("content", "답변 감사합니다. 비중과 업종 분산을 함께 살펴볼게요."))
            .header(SHARE_TOKEN_HEADER, token)
            .post(PUBLIC_API + "/comments/" + commentId + "/replies")
            .then()
            .statusCode(200)
            .extract()
            .path("replies[1].id");
    client(outsider)
        .body(Map.of("content", "다른 사람의 대댓글은 수정할 수 없어요."))
        .put("/api/users/me/portfolio/analysis/comment-replies/" + reviewerReplyId)
        .then()
        .statusCode(404)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_COMMENT_REPLY_NOT_FOUND"));
    client(reviewer)
        .body(Map.of("content", "답변 감사합니다. 비중 조절부터 다시 살펴볼게요."))
        .put("/api/users/me/portfolio/analysis/comment-replies/" + reviewerReplyId)
        .then()
        .statusCode(200);

    client(owner)
        .get(OWNER_API)
        .then()
        .statusCode(200)
        .body("[0].comments", hasSize(2))
        .body("[0].sharedAssets", hasSize(1))
        .body("[0].comments[1].replies", hasSize(2))
        .body("[0].comments[1].replies[1].content", equalTo("답변 감사합니다. 비중 조절부터 다시 살펴볼게요."))
        .body("[0].comments[0].content", equalTo("반도체 비중을 조금씩 줄이는 방향으로 검토해 보세요."))
        .body("[0].comments[1].content", equalTo("반도체 비중은 유지하되 다른 업종을 추가하는 방법도 있어요."));
    client(reviewer)
        .delete("/api/users/me/portfolio/analysis/comment-replies/" + reviewerReplyId)
        .then()
        .statusCode(200)
        .body("replies", hasSize(1));
    client(owner)
        .body(Map.of("content", "내 분석에는 내가 의견을 남길 수 없어요."))
        .header(SHARE_TOKEN_HEADER, token)
        .post(PUBLIC_API + "/comments")
        .then()
        .statusCode(403)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_SHARE_OWNER_COMMENT_FORBIDDEN"));
    client(owner)
        .body(
            Map.of(
                "analysisId",
                analysis.getId(),
                "question",
                "존재하지 않는 종목은 공개 대상으로 선택할 수 없어요.",
                "assetNames",
                List.of("없는 종목")))
        .post(OWNER_API)
        .then()
        .statusCode(400)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS"));
  }

  @Test
  void revokedLinkStopsPublicAccessButKeepsCommentsForTheOwner() {
    Member owner = member("작성자");
    Member reviewer = member("리뷰어");
    PortfolioAnalysis analysis = analysis(owner);
    var created =
        client(owner)
            .body(
                Map.of(
                    "analysisId",
                    analysis.getId(),
                    "question",
                    "현재 종목 구성에 대한 솔직한 의견을 듣고 싶어요.",
                    "assetNames",
                    List.of("삼성전자", "SK하이닉스")))
            .post(OWNER_API)
            .then()
            .extract();
    String token = created.path("token");
    int shareId = created.path("share.id");
    client(reviewer)
        .body(Map.of("content", "현재 구성은 한 업종에 다소 집중되어 있다고 생각해요."))
        .header(SHARE_TOKEN_HEADER, token)
        .post(PUBLIC_API + "/comments")
        .then()
        .statusCode(200);

    client(owner).post(OWNER_API + "/" + shareId + "/revoke").then().statusCode(200);

    given()
        .port(port)
        .header(SHARE_TOKEN_HEADER, token)
        .get(PUBLIC_API)
        .then()
        .statusCode(410)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_SHARE_REVOKED"));
    client(owner)
        .get(OWNER_API)
        .then()
        .body("[0].status", equalTo("REVOKED"))
        .body("[0].comments", hasSize(1));
  }

  @Test
  void sharesSavedPortfolioCompositionWithoutAnAnalysis() {
    Member owner = member("작성자");
    Portfolio portfolio = portfolios.save(Portfolio.create(owner));
    String code = UUID.randomUUID().toString();
    Asset asset =
        assets.save(
            Asset.listedStock(
                "삼성전자",
                code,
                StockMarket.KOSPI,
                "KR" + code.replace("-", "").substring(0, 10),
                LocalDate.now().minusDays(2)));
    PortfolioHolding holding =
        holdings.save(new PortfolioHolding(portfolio, asset, 10, new BigDecimal("70000")));
    LocalDate firstPurchasedOn = LocalDate.now().minusMonths(2);
    holdingHistories.save(
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.BUY,
            holding.getQuantity(),
            holding.getAveragePurchasePrice(),
            firstPurchasedOn,
            10L,
            new BigDecimal("70000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    prices.saveAndFlush(
        new StockDailyPrice(asset, LocalDate.now().minusDays(1), new BigDecimal("80000")));

    var created =
        client(owner)
            .body(Map.of("question", "현재 포트폴리오 구성에 대한 의견을 듣고 싶어요.", "assetNames", List.of("삼성전자")))
            .post(OWNER_API)
            .then()
            .statusCode(200)
            .body("share.analysisId", equalTo(null))
            .extract();

    Map<String, Object> publicAnalysis =
        given()
            .port(port)
            .header(SHARE_TOKEN_HEADER, created.path("token"))
            .get(PUBLIC_API)
            .then()
            .statusCode(200)
            .body("analysis.hasAnalysis", equalTo(false))
            .body("analysis.analyzedAssetCount", equalTo(0))
            .body("analysis.impacts[0].assetName", equalTo("삼성전자"))
            .body("analysis.impacts[0].weight", equalTo(100.0F))
            .body(
                "analysis.impacts[0].holding.firstPurchasedOn",
                equalTo(firstPurchasedOn.toString()))
            .extract()
            .path("analysis");
    assertThat(publicAnalysis.toString())
        .contains("returnRate")
        .doesNotContain(
            "quantity", "evaluationAmount", "profitLoss", "averagePurchasePrice", "currentPrice");
  }

  private PortfolioAnalysis analysis(Member owner) {
    Portfolio portfolio = portfolios.save(Portfolio.create(owner));
    Instant analyzedAt = Instant.now().minusSeconds(60);
    var snapshot =
        new PortfolioAnalysisSnapshot(
            List.of(
                new PortfolioAnalysisSnapshot.Asset("삼성전자", new BigDecimal("60"), 6L),
                new PortfolioAnalysisSnapshot.Asset("SK하이닉스", new BigDecimal("40"), 4L)),
            new PortfolioImpactSummary(
                ImpactDirection.POSITIVE,
                new BigDecimal("60.00"),
                new BigDecimal("60.00"),
                BigDecimal.ZERO),
            "반도체 수요 회복이 포트폴리오에 긍정적으로 작용할 수 있어요.",
            List.of(
                new PortfolioAnalysisSnapshot.AssetImpact(
                    "삼성전자",
                    ImpactDirection.POSITIVE,
                    ImpactLevel.MEDIUM,
                    "반도체 수요가 회복되고 있어요.",
                    "실적 기대가 높아질 수 있어요.",
                    "수요 추이를 확인해야 해요.",
                    "반도체 업황의 영향을 크게 받아요.",
                    "반도체 수요가 증가했어요.",
                    1),
                new PortfolioAnalysisSnapshot.AssetImpact(
                    "SK하이닉스",
                    ImpactDirection.NEGATIVE,
                    ImpactLevel.LOW,
                    "메모리 가격 변동성이 있어요.",
                    "단기 실적 변동성이 커질 수 있어요.",
                    "가격 흐름을 확인해야 해요.",
                    "메모리 업황의 영향을 받아요.",
                    "메모리 가격이 변동했어요.",
                    2)),
            Map.of(),
            new PortfolioAnalysisPersonalContext(
                LocalDate.now(),
                new BigDecimal("1000000"),
                new BigDecimal("1200000"),
                new BigDecimal("200000"),
                new BigDecimal("20"),
                List.of(
                    new PortfolioAnalysisPersonalContext.Holding(
                        "삼성전자",
                        LocalDate.now(),
                        10,
                        new BigDecimal("100000"),
                        new BigDecimal("200000"),
                        new BigDecimal("20")))));
    PortfolioAnalysis analysis =
        analyses.save(
            PortfolioAnalysis.create(
                portfolio, null, "test", "test", snapshot, analyzedAt, "최초 분석"));
    evidence.save(
        new PortfolioAnalysisEvidence(
            analysis,
            "삼성전자",
            "반도체 산업 동향",
            "https://example.invalid/source",
            LocalDate.now(),
            LocalDate.now(),
            "수요가 증가했습니다.",
            "Demand increased."));
    return analysis;
  }

  private Member member(String nickname) {
    String key = UUID.randomUUID().toString();
    return members.save(Member.createGoogleMember(key, key + "@example.invalid", nickname, null));
  }

  private RequestSpecification client(Member member) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
