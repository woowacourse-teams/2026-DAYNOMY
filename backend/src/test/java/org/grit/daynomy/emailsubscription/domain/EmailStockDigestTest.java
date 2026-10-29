package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.junit.jupiter.api.Test;

class EmailStockDigestTest {
  @Test
  void showsPriceDateAndPersonalImpactWithoutAnalysisClaims() {
    assertThat(digest(1L).emailSections("https://daynomy.com"))
        .contains("2026-10-23", "종목 등락률: 5%", "기여도: 5%p")
        .doesNotContain("분석이 달라", "출처:", "trillion");
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
