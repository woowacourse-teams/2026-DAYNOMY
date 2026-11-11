package org.grit.daynomy.league.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.ai.OpenAiLeagueReviewGuideClient.Input;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideBasis;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.WeeklyLeaderboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeagueReviewGuideContextService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final InvestmentDecisionRepository decisionRepository;
  private final InvestmentReviewRepository reviewRepository;
  private final InvestorProfileRepository profileRepository;
  private final WeeklyLeaderboardRepository leaderboardRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Context load(Long memberId, Long transactionId) {
    InvestmentDecision decision =
        decisionRepository
            .findByIdAndPortfolioMemberId(transactionId, memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    if (reviewRepository.existsByTransactionId(transactionId))
      throw new BusinessException(LeagueErrorCode.REVIEW_GUIDE_ALREADY_REVIEWED);

    Input input =
        new Input(
            decision.getAsset().getName(),
            decision.getTransactionType().name(),
            decision.getDecisionReason(),
            decision.getExpectedChange(),
            decision.getInvalidationCondition(),
            decision.getExpectedHoldingPeriod() == null
                ? null
                : decision.getExpectedHoldingPeriod().name(),
            decision.getMaximumAcceptableLossRate(),
            decision.isWrittenAfterTrade());
    return new Context(
        input, basis(memberId, decision), decision.getAsset().getId(), decision.getUpdatedAt());
  }

  private ReviewGuideBasis basis(Long memberId, InvestmentDecision decision) {
    if (decision.getUpdatedAt() == null) return null;
    InvestorProfile profile = profileRepository.findByMemberId(memberId).orElse(null);
    if (profile == null || !profile.isLeagueEnabled()) return null;
    LocalDate savedOn = LocalDate.ofInstant(decision.getUpdatedAt(), SEOUL);
    LocalDate currentWeek =
        LocalDate.ofInstant(clock.instant(), SEOUL)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    for (int offset = 0; offset < 3; offset++) {
      LocalDate week = currentWeek.minusWeeks(offset);
      if (!week.isAfter(savedOn)) continue;
      var board =
          leaderboardRepository
              .findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                  week, LeagueType.WEEKLY_RETURN)
              .orElse(null);
      if (board == null) continue;
      var entry =
          board.getEntries().stream()
              .filter(candidate -> candidate.profileId().equals(profile.getId()))
              .findFirst()
              .orElse(null);
      if (entry != null && entry.weeklyReturnRate() != null)
        return new ReviewGuideBasis(
            board.getWeekStart(), board.getAsOfDate(), entry.weeklyReturnRate());
    }
    return null;
  }

  public record Context(Input input, ReviewGuideBasis basis, Long assetId, Instant updatedAt) {}
}
