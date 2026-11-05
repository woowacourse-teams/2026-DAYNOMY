package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.exception.LeagueErrorCode;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "league_review_reminders")
public class LeagueReviewReminder extends BaseEntity {
  public static final Duration INTERVAL = Duration.ofDays(7);

  @Id
  @Column(name = "profile_id")
  private Long profileId;

  @Column(name = "participation_id", nullable = false)
  private Long participationId;

  @Column(name = "request_key")
  private UUID requestKey;

  @Column(name = "last_notified_at")
  private Instant lastNotifiedAt;

  @Column(name = "next_notification_at")
  private Instant nextNotificationAt;

  @Column(nullable = false)
  private boolean snoozed;

  public LeagueReviewReminder(Long profileId, Long participationId) {
    this.profileId = profileId;
    this.participationId = participationId;
  }

  public void useParticipation(Long participationId) {
    if (Objects.equals(this.participationId, participationId)) return;
    this.participationId = participationId;
    requestKey = null;
    lastNotifiedAt = null;
    nextNotificationAt = null;
    snoozed = false;
  }

  public boolean accepts(UUID key, Instant now) {
    if (key.equals(requestKey)) return !snoozed;
    return nextNotificationAt == null || !now.isBefore(nextNotificationAt);
  }

  public void claim(UUID key, Instant now) {
    if (key.equals(requestKey)) return;
    requestKey = key;
    lastNotifiedAt = now;
    nextNotificationAt = now.plus(INTERVAL);
    snoozed = false;
  }

  public boolean belongsTo(Long participationId, UUID key) {
    return Objects.equals(this.participationId, participationId) && key.equals(requestKey);
  }

  public void snooze(Instant now) {
    if (snoozed) return;
    if (nextNotificationAt == null || !now.isBefore(nextNotificationAt)) {
      throw new BusinessException(LeagueErrorCode.REVIEW_REMINDER_EXPIRED);
    }
    nextNotificationAt = now.plus(INTERVAL);
    snoozed = true;
  }
}
