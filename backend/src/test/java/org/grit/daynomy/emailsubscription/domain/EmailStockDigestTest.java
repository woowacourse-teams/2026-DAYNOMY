package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.junit.jupiter.api.Test;

class EmailStockDigestTest {
  @Test
  void showsPriceDateAndPersonalImpactWithoutAnalysisClaims() {
    assertThat(digest(1L).emailSections("https://daynomy.com"))
        .contains("2026-10-23", "종목 가격이 5% 올랐어요.", "내 자산의 100%를 차지해요.", "약 +5%p 기여했어요.")
        .doesNotContain("분석이 달라", "출처:", "trillion");
  }

  @Test
  void explainsPriceDirectionAndContributionWithoutClaimingTotalPortfolioReturn() {
    var positive =
        new EmailStockDigest.Stock(
            1L, "삼성전자", new BigDecimal("5.2"), new BigDecimal("40"), new BigDecimal("2.08"));
    var negative =
        new EmailStockDigest.Stock(
            2L, "다른 종목", new BigDecimal("-3.45"), new BigDecimal("60"), new BigDecimal("-2.07"));
    var content =
        EmailStockDigest.current(
            MAIL_DATE,
            MAIL_DATE.minusDays(1),
            Map.of(1L, 1L, 2L, 1L),
            List.of(positive, negative),
            List.of());
    assertThat(content.emailSections("https://daynomy.com"))
        .contains(
            "삼성전자 가격이 5.2% 올랐어요.",
            "내 자산의 40%를 차지해요.",
            "전체 자산 수익률에 약 +2.08%p 기여했어요.",
            "다른 종목 가격이 3.45% 내렸어요.",
            "전체 자산 수익률에 약 -2.07%p 기여했어요.",
            "현재 보유 수량 기준")
        .doesNotContain("전체 자산이 2.08% 올랐", "종목 등락률", "가격 변동 기여도");
  }

  @Test
  void unknownWeightsDoNotProducePersonalImpactAndFlatPricesAreNotAnIncrease() {
    var stock = new EmailStockDigest.Stock(1L, "종목", BigDecimal.ZERO, null, null);
    var content =
        EmailStockDigest.current(
            MAIL_DATE, MAIL_DATE.minusDays(1), Map.of(1L, 1L), List.of(stock), List.of());
    assertThat(content.emailSections("https://daynomy.com"))
        .contains("종목 가격은 변동이 없어요.")
        .doesNotContain("올랐어요", "내렸어요", "내 자산의", "기여했어요");
  }

  @Test
  void newRelatedMaterialUsesRegistrationDateRatherThanClaimingItWasPublishedToday() {
    var item =
        new EmailStockDigest.Content(
            1L,
            "key",
            1L,
            "종목",
            ContentSourceType.THREADS,
            "등록된 글",
            "https://threads.net/post/1",
            NOW.minusSeconds(60));
    var digest = EmailStockDigest.current(null, null, Map.of(1L, 1L), List.of(), List.of(item));
    assertThat(digest.emailSections("https://daynomy.com"))
        .contains("등록된 글", "데이노미 등록일 2026-10-26", "https://threads.net/post/1")
        .doesNotContain("오늘 게시", "분석이 달라", "종목 등락률");
  }

  @Test
  void legacyAiSnapshotsAreReadableButNeverCurrentNotifications() throws Exception {
    var json =
        "{\"baseDate\":\"2026-10-23\",\"previousBaseDate\":\"2026-10-22\",\"stocks\":[{\"analysisId\":3,\"assetId\":1,\"name\":\"종목\",\"content\":{\"reason\":\"old\"}}]}";
    var old = new ObjectMapper().findAndRegisterModules().readValue(json, EmailStockDigest.class);
    var arrayJson =
        json.replace("\"2026-10-23\"", "[2026,10,23]").replace("\"2026-10-22\"", "[2026,10,22]");
    assertThat(
            new ObjectMapper()
                .findAndRegisterModules()
                .readValue(arrayJson, EmailStockDigest.class)
                .currentNotification())
        .isFalse();
    assertThat(old.currentNotification()).isFalse();
    assertThat(old.stocks()).hasSize(1);
    assertThat(old.contents()).isEmpty();
  }

  @Test
  void rejectsOtherMembersAssetsAndMoreThanThreeItems() {
    assertThatThrownBy(
            () ->
                EmailStockDigest.current(
                    MAIL_DATE, MAIL_DATE, Map.of(2L, 1L), digest(1L).stocks(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                EmailStockDigest.current(
                    MAIL_DATE,
                    MAIL_DATE,
                    Map.of(1L, 1L),
                    Collections.nCopies(4, digest(1L).stocks().getFirst()),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
