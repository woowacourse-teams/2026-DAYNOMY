package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.WeeklyLeaderboard;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.WeeklyLeaderboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeagueReviewGuideContextServiceTest {
  private final InvestmentDecisionRepository decisions = mock(InvestmentDecisionRepository.class);
  private final InvestmentReviewRepository reviews = mock(InvestmentReviewRepository.class);
  private final InvestorProfileRepository profiles = mock(InvestorProfileRepository.class);
  private final WeeklyLeaderboardRepository boards = mock(WeeklyLeaderboardRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);
  private final LeagueReviewGuideContextService service =
      new LeagueReviewGuideContextService(decisions, reviews, profiles, boards, clock);

  private InvestmentDecision decision;

  @BeforeEach
  void setUp() {
    decision = mock(InvestmentDecision.class);
  }

  @Test
  void refusesAnotherMembersRecordBeforeReadingItsContents() {
    assertThatThrownBy(() -> service.load(1L, 7L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    verifyNoInteractions(reviews, profiles, boards);
  }

  @Test
  void refusesAnAlreadyReviewedRecord() {
    given(decisions.findByIdAndPortfolioMemberId(7L, 1L)).willReturn(Optional.of(decision));
    given(reviews.existsByTransactionId(7L)).willReturn(true);

    assertThatThrownBy(() -> service.load(1L, 7L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode())
                    .isEqualTo(LeagueErrorCode.REVIEW_GUIDE_ALREADY_REVIEWED));
    verifyNoInteractions(profiles, boards);
  }

  @Test
  void usesOnlyAConfirmedWeekAfterTheDecisionWasLastSaved() {
    givenDecision(Instant.parse("2026-10-17T10:00:00Z"));
    InvestorProfile profile = mock(InvestorProfile.class);
    given(profile.isLeagueEnabled()).willReturn(true);
    given(profile.getId()).willReturn(9L);
    given(profiles.findByMemberId(1L)).willReturn(Optional.of(profile));
    WeeklyLeaderboard.Entry entry = mock(WeeklyLeaderboard.Entry.class);
    given(entry.profileId()).willReturn(9L);
    given(entry.weeklyReturnRate()).willReturn(new BigDecimal("1.25"));
    LocalDate week = LocalDate.of(2026, 10, 19);
    WeeklyLeaderboard board =
        new WeeklyLeaderboard(
            week,
            LeagueType.WEEKLY_RETURN,
            2,
            LocalDate.of(2026, 10, 23),
            "FINALIZED",
            List.of(entry));
    given(
            boards.findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                week, LeagueType.WEEKLY_RETURN))
        .willReturn(Optional.of(board));

    var result = service.load(1L, 7L);

    assertThat(result.basis().weekStart()).isEqualTo(week);
    assertThat(result.basis().portfolioWeeklyReturnRate()).isEqualByComparingTo("1.25");
    assertThat(result.input().reason()).isEqualTo("비용 감소 기대");
  }

  @Test
  void omitsPerformanceSavedBeforeTheCurrentReason() {
    givenDecision(Instant.parse("2026-10-20T10:00:00Z"));
    InvestorProfile profile = mock(InvestorProfile.class);
    given(profile.isLeagueEnabled()).willReturn(true);
    given(profiles.findByMemberId(1L)).willReturn(Optional.of(profile));

    assertThat(service.load(1L, 7L).basis()).isNull();
    verify(boards)
        .findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
            LocalDate.of(2026, 10, 26), LeagueType.WEEKLY_RETURN);
  }

  private void givenDecision(Instant updatedAt) {
    Asset asset = mock(Asset.class);
    given(asset.getName()).willReturn("예시 종목");
    given(asset.getId()).willReturn(2L);
    given(decision.getAsset()).willReturn(asset);
    given(decision.getTransactionType()).willReturn(TransactionType.HOLD);
    given(decision.getDecisionReason()).willReturn("비용 감소 기대");
    given(decision.getUpdatedAt()).willReturn(updatedAt);
    given(decisions.findByIdAndPortfolioMemberId(7L, 1L)).willReturn(Optional.of(decision));
  }
}
