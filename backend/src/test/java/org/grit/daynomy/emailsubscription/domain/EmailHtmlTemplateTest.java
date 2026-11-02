package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;

import java.math.BigDecimal;
import java.util.*;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class EmailHtmlTemplateTest {
  private final String base = "https://daynomy.com",
      unsubscribe = "https://example.invalid/unsubscribe?token=a&next=b";

  @Test
  void pricesAndRelatedMaterialsHaveLinksAndAnHonestAnalysisInvitation() {
    var item =
        new EmailStockDigest.Content(
            1L,
            "key",
            1L,
            "종목",
            ContentSourceType.YOUTUBE,
            "새 영상",
            "https://youtube.com/watch?v=abc",
            NOW.minusSeconds(60));
    var content =
        EmailStockDigest.current(
            MAIL_DATE.minusDays(3),
            MAIL_DATE.minusDays(4),
            Map.of(1L, 1L),
            digest(1L).stocks(),
            List.of(item));
    var html = render(content, EmailDigestSupplement.empty());
    assertThat(html)
        .contains(
            "DAYNOMY",
            "새 영상",
            "종목 가격이 5% 올랐어요.",
            "자세한 자산 현황은 포트폴리오에서 확인해보세요.",
            "데이노미 등록일 2026-10-26")
        .doesNotContain("분석이 달라졌어요", "분석이 바뀌었을", "출처:", "trillion", "내 자산의 변화예요");
    assertThat(Jsoup.parse(html).select("a").eachAttr("href"))
        .contains(item.url(), base + "/", base + "/mypage", unsubscribe);
  }

  @Test
  void assetsOnlyMailOmitsEmptyPricesAndMaterials() {
    var empty = EmailStockDigest.current(null, null, Map.of(1L, 1L), List.of(), List.of());
    var assets =
        new EmailDigestSupplement(
            new EmailDigestSupplement.Assets(
                MAIL_DATE.minusDays(3),
                new BigDecimal("1000000"),
                new BigDecimal("20000"),
                new BigDecimal("2")));
    var html = render(empty, assets);
    assertThat(html)
        .contains("1,000,000", "+20,000원 · +2%", "2026-10-23 종가 기준")
        .doesNotContain("보유 종목의 가격에 변화", "새 관련 자료가 등록");
  }

  @Test
  void untrustedContentIsEscapedAndUnsafeLinksAreNotClickable() {
    var item =
        new EmailStockDigest.Content(
            1L,
            "key",
            1L,
            "<img src=x onerror=alert(1)>",
            ContentSourceType.THREADS,
            "<script>alert('x')</script>",
            "javascript:alert(1)",
            NOW);
    var content = EmailStockDigest.current(null, null, Map.of(1L, 1L), List.of(), List.of(item));
    var html = render(content, EmailDigestSupplement.empty());
    var doc = Jsoup.parse(html);
    assertThat(doc.select("script,img,[onerror]")).isEmpty();
    assertThat(doc.select("a").eachAttr("href")).allMatch(url -> url.startsWith("https://"));
    assertThat(html).contains("&lt;script&gt;", "&amp;next=b");
  }

  @Test
  void completedUpdateShowsOnlyNotificationAndSavedAnalysisLink() {
    var empty = EmailStockDigest.current(null, null, Map.of(1L, 1L), List.of(), List.of());
    var analysis =
        new EmailDigestSupplement.Analysis(100L, NOW.minusSeconds(3600), List.of("삼성전자"));
    var supplement = new EmailDigestSupplement(null, analysis);
    var mail = new EmailDelivery(member(), NOW, empty, supplement, MAIL_DATE);
    mail.attachUnsubscribeLink(unsubscribe);
    mail.completeTemplate("회원", base);
    assertThat(supplement.shouldSend()).isTrue();
    assertThat(Jsoup.parse(mail.getHtmlBody()).text())
        .contains("보유 종목의 분석이 업데이트됐어요", "삼성전자의 새 근거를 반영했어요", "업데이트된 분석 보기", "2026-10-26 08:00")
        .doesNotContain("새로 실행", "보유 종목의 가격에 변화", "새 관련 자료가 등록");
    assertThat(mail.getBody()).contains(analysis.body()).doesNotContain("새로 실행");
  }

  @Test
  void placesUpdateAndSingleButtonImmediatelyAfterAssetsInHtmlAndPlainText() {
    var item =
        new EmailStockDigest.Content(
            1L,
            "key",
            1L,
            "종목",
            ContentSourceType.YOUTUBE,
            "새 영상",
            "https://youtube.com/watch?v=abc",
            NOW.minusSeconds(60));
    var profile =
        EmailStockDigest.current(
            MAIL_DATE.minusDays(3),
            MAIL_DATE.minusDays(4),
            Map.of(1L, 1L),
            digest(1L).stocks(),
            List.of(item));
    var assets =
        new EmailDigestSupplement.Assets(
            MAIL_DATE.minusDays(3),
            new BigDecimal("1000000"),
            new BigDecimal("20000"),
            new BigDecimal("2"));
    var notice = new EmailDigestSupplement.Analysis(100L, NOW.minusSeconds(3600), List.of("종목"));
    var mail =
        new EmailDelivery(
            member(), NOW, profile, new EmailDigestSupplement(assets, notice), MAIL_DATE);
    mail.attachUnsubscribeLink(unsubscribe);
    mail.completeTemplate("회원", base);
    var html = mail.getHtmlBody();
    for (var text : List.of(Jsoup.parse(html).text(), mail.getBody())) {
      assertThat(text)
          .containsSubsequence(
              "내 자산의 변화예요",
              "보유 종목의 분석이 업데이트됐어요",
              "업데이트된 분석 보기",
              "보유 종목의 가격에 변화가 있어요",
              "종목 가격이 5% 올랐어요.",
              "보유 종목에 새 관련 자료가 등록됐어요",
              "알림 설정");
      assertThat(text).doesNotContain("내 자산의 주요 변화와 새 관련 자료를 모아 전해드려요.", "내 포트폴리오 확인하기");
    }
    assertThat(Jsoup.parse(html).select("a[href='" + base + "/']")).hasSize(1);
  }

  private String render(EmailStockDigest digest, EmailDigestSupplement supplement) {
    return EmailHtmlTemplate.render("회원", MAIL_DATE, digest, supplement, base, unsubscribe);
  }
}
