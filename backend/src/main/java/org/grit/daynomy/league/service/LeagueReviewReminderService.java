package org.grit.daynomy.league.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueParticipation;
import org.grit.daynomy.league.domain.LeagueReviewReminder;
import org.grit.daynomy.league.dto.LeagueDto.DecisionResponse;
import org.grit.daynomy.league.dto.LeagueDto.ReviewReminderResponse;
import org.grit.daynomy.league.dto.LeagueDto.ReviewReminderSnoozeResponse;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.LeagueReviewReminderRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeagueReviewReminderService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final InvestorProfileRepository profiles;
  private final SharedPortfolioRepository portfolios;
  private final InvestmentDecisionRepository decisions;
  private final PortfolioDailyReturnRepository returns;
  private final LeagueReviewReminderRepository reminders;
  private final Clock clock;

  @Transactional
  public ReviewReminderResponse claim(Long memberId, UUID requestKey) {
    // The profile lock serializes claims from multiple tabs/devices, including first delivery.
    var profile = profiles.findByMemberIdForUpdate(memberId).orElse(null);
    var participation = activeParticipation(profile);
    var now = clock.instant();
    if (participation == null
        || now.isBefore(participation.getStartedAt().plus(LeagueReviewReminder.INTERVAL))) {
      return new ReviewReminderResponse(null, List.of(), null);
    }
    var reminder =
        reminders
            .findById(profile.getId())
            .orElseGet(() -> new LeagueReviewReminder(profile.getId(), participation.getId()));
    reminder.useParticipation(participation.getId());
    if (!reminder.accepts(requestKey, now)) {
      return new ReviewReminderResponse(null, List.of(), reminder.getNextNotificationAt());
    }
    var portfolio = portfolios.findByMemberId(memberId).orElse(null);
    if (portfolio == null
        || !returns.existsLatestEligibleByPortfolioIdAndBaseDateBetween(
            portfolio.getId(), participation.eligibleFrom(), now.atZone(SEOUL).toLocalDate())) {
      return new ReviewReminderResponse(null, List.of(), reminder.getNextNotificationAt());
    }
    var pending =
        decisions.findUnreviewedCreatedBefore(memberId, now.minus(LeagueReviewReminder.INTERVAL));
    if (pending.isEmpty()) {
      return new ReviewReminderResponse(null, List.of(), reminder.getNextNotificationAt());
    }
    reminder.claim(requestKey, now);
    reminders.save(reminder);
    return new ReviewReminderResponse(
        requestKey,
        pending.stream().map(record -> DecisionResponse.from(record, List.of())).toList(),
        reminder.getNextNotificationAt());
  }

  @Transactional
  public ReviewReminderSnoozeResponse snooze(Long memberId, UUID requestKey) {
    var profile = profiles.findByMemberIdForUpdate(memberId).orElse(null);
    var participation = activeParticipation(profile);
    var reminder = profile == null ? null : reminders.findById(profile.getId()).orElse(null);
    if (participation == null
        || reminder == null
        || !reminder.belongsTo(participation.getId(), requestKey)) {
      throw new BusinessException(LeagueErrorCode.REVIEW_REMINDER_EXPIRED);
    }
    reminder.snooze(clock.instant());
    return new ReviewReminderSnoozeResponse(reminder.getNextNotificationAt());
  }

  private LeagueParticipation activeParticipation(InvestorProfile profile) {
    if (profile == null || !profile.isLeagueEnabled()) return null;
    return profile.getParticipations().stream()
        .filter(period -> period.getEndedAt() == null)
        .max(Comparator.comparing(LeagueParticipation::getStartedAt))
        .orElse(null);
  }
}
