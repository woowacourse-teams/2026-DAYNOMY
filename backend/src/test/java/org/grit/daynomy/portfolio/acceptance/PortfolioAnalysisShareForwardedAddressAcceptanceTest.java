package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisShareRepository;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "portfolio-analysis-share.read-rate-limit-per-minute=1")
class PortfolioAnalysisShareForwardedAddressAcceptanceTest extends SharedDatabaseTest {

  private static final String PUBLIC_API = "/api/portfolio/analysis/shares";
  private static final String SHARE_TOKEN_HEADER = "X-Portfolio-Share-Token";

  @LocalServerPort int port;
  @Autowired MemberRepository members;
  @Autowired PortfolioAnalysisShareRepository shares;

  @Test
  void separatesReadLimitsByForwardedAddressFromTheTrustedLocalProxy() {
    String rawToken = "forwarded-address-token";
    Member owner =
        members.save(
            Member.createGoogleMember(
                UUID.randomUUID().toString(),
                UUID.randomUUID() + "@example.invalid",
                "프록시 테스트",
                null));
    shares.saveAndFlush(
        PortfolioAnalysisShare.create(
            owner,
            1L,
            "프록시 뒤에서도 사용자별로 조회 제한을 적용해 주세요.",
            hash(rawToken),
            snapshot(),
            Instant.now().plusSeconds(60)));

    request(rawToken, "203.0.113.10").get(PUBLIC_API).then().statusCode(200);
    request(rawToken, "203.0.113.10")
        .get(PUBLIC_API)
        .then()
        .statusCode(429)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_SHARE_RATE_LIMITED"));
    request(rawToken, "203.0.113.11").get(PUBLIC_API).then().statusCode(200);
  }

  private io.restassured.specification.RequestSpecification request(
      String token, String forwardedAddress) {
    return given()
        .port(port)
        .header(SHARE_TOKEN_HEADER, token)
        .header("X-Forwarded-For", forwardedAddress);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private PortfolioAnalysisPublicSnapshot snapshot() {
    return new PortfolioAnalysisPublicSnapshot(
        0,
        0,
        true,
        false,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        "",
        List.of(),
        List.of(),
        List.of());
  }
}
