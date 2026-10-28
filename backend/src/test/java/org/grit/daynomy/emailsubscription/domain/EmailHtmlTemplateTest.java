package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class EmailHtmlTemplateTest {
  private final LocalDate date = LocalDate.of(2026, 10, 28);
  private final String base = "https://example.invalid";
  private final String unsubscribe = "https://example.invalid/unsubscribe?token=a&next=b";

  @Test
  void assetOnlyMailShowsFormattedFiguresAndTheActualPriceDateWithoutEmptySections() {
    var supplement =
        new EmailDigestSupplement(
            new EmailDigestSupplement.Assets(
                date.minusDays(2),
                new BigDecimal("1000000"),
                new BigDecimal("12345.67"),
                new BigDecimal("1.2500")),
            List.of());
    var html = render(null, supplement);
    assertThat(html)
        .contains(
            "DAYNOMY",
            "2026.10.28",
            "2026-10-26 종가 기준",
            "1,000,000",
            "+12,345.67원 · +1.25%",
            "#0a0a0a")
        .doesNotContain("다가오는 투자 일정이에요", "보유 종목에 이런 소식이 있어요", "포트폴리오 분석이 달라졌어요");
    var document = Jsoup.parse(html);
    assertThat(document.select("a").eachAttr("href"))
        .contains(base + "/", base + "/mypage", unsubscribe);
    assertThat(document.select("h1").text()).isEqualTo("내 투자 주요 변화와 일정");
    assertThat(document.select("table").eachAttr("role")).containsOnly("presentation");
  }

  @Test
  void calendarOnlyMailDistinguishesTomorrowAndUnknownAnnouncementTime() {
    var supplement =
        new EmailDigestSupplement(
            null,
            List.of(
                new EmailDigestSupplement.Event(
                    "1", "주요 경제 일정", Instant.parse("2026-10-29T05:30:00Z"), base + "/economic"),
                new EmailDigestSupplement.Event(
                    "2",
                    "보유 종목 실적 발표",
                    Instant.parse("2026-10-29T00:00:00Z"),
                    base + "/earnings",
                    false)));
    var html = render(null, supplement);
    assertThat(Jsoup.parse(html).text())
        .contains("다가오는 투자 일정이에요", "내일 · 2026-10-29 14:30 · 한국 시간", "내일 · 2026-10-29 · 발표 시각 미확정")
        .doesNotContain("전체 평가금액", "내 자산의 변화예요", "포트폴리오 분석이 달라졌어요");
  }

  @Test
  void firstAnalysisIncludesActualNewsAndEvidenceWithoutInventingAnAnalysisChange() {
    var digest =
        digest(
                new EmailStockDigest.Stock(
                    1L, 2L, "테스트 종목", null, null, null, content("첫 분석 근거", base + "/source")))
            .newInformation(Map.of(), Set.of());
    var html = render(digest, EmailDigestSupplement.empty());
    assertThat(html)
        .contains("보유 종목에 이런 소식이 있어요", "부분 분석", "첫 분석 근거", "확인한 원문 인용", "처음 안내하는 종목 분석입니다.")
        .doesNotContain("포트폴리오 분석이 달라졌어요", "내 자산의 변화예요", "가격 변화를 확인하세요");
  }

  @Test
  void changedAnalysisShowsPreviousAndCurrentReasonsOnlyWhenComparisonExists() {
    var stock =
        new EmailStockDigest.Stock(
            1L,
            2L,
            "테스트 종목",
            null,
            null,
            null,
            content("새로운 근거", base + "/new"),
            "새 근거에 따른 분석 변화",
            content("이전 근거", base + "/old"),
            true);
    var html = render(digest(stock), EmailDigestSupplement.empty());
    assertThat(html)
        .contains("포트폴리오 분석이 달라졌어요", "이전 분석 근거: 이전 근거", "갱신된 분석: 새로운 근거", "새 근거에 따른 분석 변화");
  }

  @Test
  void priceOnlyInformationDoesNotRepeatNewsOrInventMissingPersonalFigures() {
    var stock =
        new EmailStockDigest.Stock(
            1L,
            2L,
            "테스트 종목",
            new BigDecimal("-5.25"),
            null,
            null,
            content("저장된 분석", base + "/source"),
            "새 가격 기준일의 중요한 변동입니다.",
            null,
            false);
    var html = render(digest(stock), EmailDigestSupplement.empty());
    assertThat(html)
        .contains("보유 종목의 가격 변화를 확인하세요", "종목 등락률 -5.25%")
        .doesNotContain("보유 종목에 이런 소식이 있어요", "포트폴리오 분석이 달라졌어요", "종가 비중", "가격 변동 기여도", "저장된 분석");
  }

  @Test
  void escapesUntrustedTextAndKeepsDangerousUrlsOutOfLinks() {
    var stock =
        new EmailStockDigest.Stock(
            1L,
            2L,
            "<img src=x onerror=alert(1)>",
            null,
            null,
            null,
            content("<script>alert('x')</script>\n다음 줄", "javascript:alert(1)"),
            "첫 분석",
            null,
            true);
    var html =
        EmailHtmlTemplate.render(
            "<svg onload=alert(1)>",
            date,
            digest(stock),
            EmailDigestSupplement.empty(),
            base,
            "data:text/html,unsafe");
    var document = Jsoup.parse(html);
    assertThat(document.select("script,img,svg,[onerror],[onclick],[onload]")).isEmpty();
    assertThat(document.select("a").eachAttr("href")).allMatch(url -> url.startsWith("https://"));
    assertThat(html).contains("&lt;script&gt;", "&lt;img", "<br>다음 줄");
  }

  @Test
  void escapesAmpersandsInLinkAttributesWithoutChangingTheDestination() {
    var html =
        render(
            null,
            new EmailDigestSupplement(
                null,
                List.of(
                    new EmailDigestSupplement.Event(
                        "1",
                        "투자 일정",
                        Instant.parse("2026-10-29T05:30:00Z"),
                        base + "/source?a=1&b=2"))));
    assertThat(html).contains("href=\"https://example.invalid/source?a=1&amp;b=2\"");
    assertThat(Jsoup.parse(html).select("a").eachAttr("href")).contains(base + "/source?a=1&b=2");
  }

  private String render(EmailStockDigest digest, EmailDigestSupplement supplement) {
    return EmailHtmlTemplate.render("회원", date, digest, supplement, base, unsubscribe);
  }

  private EmailStockDigest digest(EmailStockDigest.Stock stock) {
    return new EmailStockDigest(date.minusDays(1), date.minusDays(2), List.of(stock));
  }

  private CommonStockAnalysisContent content(String reason, String url) {
    return new CommonStockAnalysisContent(
        ImpactDirection.NEUTRAL,
        ImpactLevel.LOW,
        "확인된 새 소식",
        "예상 반응",
        "전망",
        reason,
        List.of(new Source("자료", url, date.minusDays(1), date.minusDays(1), "본문", "확인한 원문 인용")));
  }
}
