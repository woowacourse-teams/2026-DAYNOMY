package org.grit.daynomy.investmentcalendar.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.service.TokenService;
import org.grit.daynomy.investmentcalendar.domain.*;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.*;
import org.grit.daynomy.portfolio.repository.*;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvestmentCalendarAcceptanceTest extends SharedDatabaseTest {
  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired TokenService tokens;
  @Autowired AssetRepository assets;
  @Autowired PortfolioRepository portfolios;
  @Autowired PortfolioHoldingRepository holdings;
  @Autowired InvestmentEventRepository events;

  @Test
  void memberAndGuestUseTheirOwnHoldingsAndRealSecurityAndMonthlyQueries() {
    // A separate month keeps fixture events out of other acceptance scenarios.
    String suffix = UUID.randomUUID().toString();
    var member =
        members.save(
            Member.createGoogleMember(
                suffix, suffix + "@example.invalid", "calendar-" + suffix.substring(0, 8), null));
    var asset = assets.save(new Asset("보유종목", AssetCategory.STOCK, suffix));
    var portfolio = portfolios.save(Portfolio.create(member));
    holdings.save(new PortfolioHolding(portfolio, asset, 2, BigDecimal.TEN));
    var macro = event(InvestmentEventType.US_CPI, "2098-09-30T15:00:00Z", null);
    var own = event(InvestmentEventType.CORPORATE_EARNINGS, "2098-10-02T00:00:00Z", suffix);
    var other =
        event(InvestmentEventType.CORPORATE_EARNINGS, "2098-10-03T00:00:00Z", "other-" + suffix);
    event(InvestmentEventType.US_CPI, "2098-10-31T15:00:00Z", null);
    String path = "/api/users/me/investment-calendar?year=2098&month=10";
    given().port(port).get(path).then().statusCode(401);
    var token = tokens.issue(member.getId()).accessToken();
    given()
        .port(port)
        .cookie("access_token", token)
        .get(path)
        .then()
        .statusCode(200)
        .body("events.id", contains(macro.getId().intValue(), own.getId().intValue()));
    given()
        .port(port)
        .cookie("access_token", token)
        .get(path + "&scope=ALL")
        .then()
        .statusCode(200)
        .body(
            "events.id",
            contains(macro.getId().intValue(), own.getId().intValue(), other.getId().intValue()));
    var body = Map.of("holdings", List.of(Map.of("assetId", asset.getId(), "quantity", 2)));
    String guest = "/api/investment-calendar?year=2098&month=10";
    given()
        .port(port)
        .contentType("application/json")
        .body(body)
        .post(guest)
        .then()
        .statusCode(403);
    var csrf = given().port(port).get("/api/auth/csrf");
    given()
        .port(port)
        .cookie("XSRF-TOKEN", csrf.cookie("XSRF-TOKEN"))
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .contentType("application/json")
        .body(body)
        .post(guest)
        .then()
        .statusCode(200)
        .body("events.id", contains(macro.getId().intValue(), own.getId().intValue()));
    given()
        .port(port)
        .cookie("XSRF-TOKEN", csrf.cookie("XSRF-TOKEN"))
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .contentType("application/json")
        .body(Map.of("holdings", List.of()))
        .post(guest)
        .then()
        .statusCode(200)
        .body("events.id", contains(macro.getId().intValue()));
  }

  private InvestmentEvent event(InvestmentEventType type, String at, String code) {
    return events.save(
        new InvestmentEvent(
            type,
            "발표",
            Instant.parse(at),
            null,
            null,
            "%",
            "출처",
            "https://example.invalid/event",
            UUID.randomUUID().toString(),
            code));
  }
}
