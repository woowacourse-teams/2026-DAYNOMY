package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisCheckTest {
  @Test
  void allowsThreeManualRefreshesAndResetsAtKoreanMidnight() {
    var beforeMidnight = Instant.parse("2026-10-29T14:59:59Z");
    for (int count = 0; count < 3; count++) {
      assertThat(check.remainingRefreshCount(beforeMidnight)).isEqualTo(3 - count);
      assertThat(check.reserveManualRefresh(beforeMidnight)).isTrue();
    }
    assertThat(check.remainingRefreshCount(beforeMidnight)).isZero();
    assertThat(check.reserveManualRefresh(beforeMidnight)).isFalse();
    var midnight = Instant.parse("2026-10-29T15:00:00Z");
    assertThat(PortfolioAnalysisCheck.refreshResetAt(beforeMidnight)).isEqualTo(midnight);
    assertThat(check.remainingRefreshCount(midnight)).isEqualTo(3);
    assertThat(check.reserveManualRefresh(midnight)).isTrue();
    assertThat(check.remainingRefreshCount(midnight)).isEqualTo(2);
  }

  @Test
  void processingAutomaticAndInitialAnalysisDoNotConsumeManualRefreshes() {
    check.begin(UUID.randomUUID(), first, Duration.ofMinutes(5));
    check.recordSuccess(first, PortfolioAnalysisCheck.Outcome.UPDATED);
    assertThat(check.remainingRefreshCount(first)).isEqualTo(3);
  }

  @Test
  void rejectedRefreshReleasesOneReservationWithoutGoingBelowZero() {
    assertThat(check.reserveManualRefresh(first)).isTrue();
    check.releaseManualRefresh();
    check.releaseManualRefresh();
    assertThat(check.remainingRefreshCount(first)).isEqualTo(3);
  }

  private final Instant first = Instant.parse("2026-10-12T07:00:00Z");
  private final PortfolioAnalysisCheck check =
      new PortfolioAnalysisCheck(
          Portfolio.create(Member.createGoogleMember("test", "test@example.invalid", "테스트", null)));

  @Test
  void recheckIsRequiredAtTwentyFourHoursButDoesNotEraseSuccessfulCheck() {
    check.recordSuccess(first, PortfolioAnalysisCheck.Outcome.UPDATED);
    assertThat(check.requiresRecheck(first.plusSeconds(86399))).isFalse();
    assertThat(check.requiresRecheck(first.plusSeconds(86400))).isTrue();
    check.recordSuccess(first.plusSeconds(86401), PortfolioAnalysisCheck.Outcome.UNCONFIRMED);
    assertThat(check.requiresRecheck(first.plusSeconds(86402))).isTrue();
    check.recordSuccess(first.plusSeconds(86403), PortfolioAnalysisCheck.Outcome.UNCHANGED);
    assertThat(check.requiresRecheck(first.plusSeconds(86404))).isFalse();
  }

  @Test
  void activeRequestCannotBeReplacedAndOnlyOwnerCanFinish() {
    UUID token = UUID.randomUUID();
    assertThat(check.begin(token, first, Duration.ofMinutes(5))).isTrue();
    assertThat(check.begin(UUID.randomUUID(), first.plusSeconds(1), Duration.ofMinutes(5)))
        .isFalse();
    check.finish(UUID.randomUUID());
    assertThat(check.ownsRequest(token, first.plusSeconds(1))).isTrue();
    check.finish(token);
    assertThat(check.isProcessing(first.plusSeconds(1))).isFalse();
  }

  @Test
  void expiredRequestCanBeReplacedAndOldWorkerCannotFinishNewRequest() {
    UUID old = UUID.randomUUID();
    UUID current = UUID.randomUUID();
    check.begin(old, first, Duration.ofMinutes(5));
    assertThat(check.ownsRequest(old, first.plusSeconds(300))).isFalse();
    assertThat(check.begin(current, first.plusSeconds(300), Duration.ofMinutes(5))).isTrue();
    check.finish(old);
    assertThat(check.ownsRequest(current, first.plusSeconds(301))).isTrue();
  }

  @Test
  @DisplayName("검색 실패를 최근 확인 성공으로 기록하지 않는다")
  void failurePreservesLastSuccessfulCheck() {
    check.recordSuccess(first);
    check.recordFailure(first.plusSeconds(60), "EXTERNAL_API_ERROR");

    assertThat(check.getLastSucceededAt()).isEqualTo(first);
    assertThat(check.getLastFailedAt()).isEqualTo(first.plusSeconds(60));
    assertThat(check.getFailureCode()).isEqualTo("EXTERNAL_API_ERROR");
  }

  @Test
  @DisplayName("성공한 재확인은 기존 실패 기록과 별도로 갱신한다")
  void successAfterFailureKeepsSeparateTimestamps() {
    check.recordFailure(first, "EXTERNAL_API_ERROR");
    check.recordSuccess(first.plusSeconds(60));

    assertThat(check.getLastSucceededAt()).isEqualTo(first.plusSeconds(60));
    assertThat(check.getLastFailedAt()).isEqualTo(first);
  }

  @Test
  @DisplayName("늦게 도착한 오래된 확인 결과가 최근 성공과 실패 시각을 되돌리지 않는다")
  void olderResultsDoNotMoveTimestampsBackwards() {
    check.recordSuccess(first.plusSeconds(60));
    check.recordSuccess(first);
    check.recordFailure(first.plusSeconds(60), "EXTERNAL_API_ERROR");
    check.recordFailure(first, "STALE_ERROR");

    assertThat(check.getLastSucceededAt()).isEqualTo(first.plusSeconds(60));
    assertThat(check.getLastFailedAt()).isEqualTo(first.plusSeconds(60));
    assertThat(check.getFailureCode()).isEqualTo("EXTERNAL_API_ERROR");
  }

  @Test
  @DisplayName("원본 예외 메시지는 실패 원인으로 저장할 수 없다")
  void rejectsRawErrorMessages() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> check.recordFailure(first, "request failed: response body"));
    assertThat(check.getLastFailedAt()).isNull();
    assertThat(check.getFailureCode()).isNull();
  }
}
