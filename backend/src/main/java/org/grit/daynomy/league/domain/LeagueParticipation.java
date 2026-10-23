package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "league_participations")
public class LeagueParticipation extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "profile_id", nullable = false)
  private InvestorProfile profile;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "performance_started_at")
  private Instant performanceStartedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  public LeagueParticipation(InvestorProfile profile, Instant startedAt) {
    this.profile = profile;
    this.startedAt = startedAt;
  }

  public void end(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public boolean contains(Instant close) {
    return !close.isBefore(calculationStartedAt()) && (endedAt == null || close.isBefore(endedAt));
  }

  public Instant calculationStartedAt() {
    return performanceStartedAt == null ? startedAt : performanceStartedAt;
  }

  public void includeCurrentWeekFrom(Instant from) {
    var zone = ZoneId.of("Asia/Seoul");
    var weekStart =
        startedAt
            .atZone(zone)
            .toLocalDate()
            .with(java.time.DayOfWeek.MONDAY)
            .atStartOfDay(zone)
            .toInstant();
    if (from.isBefore(weekStart) || !from.isBefore(startedAt)) {
      throw new IllegalArgumentException("참여한 주의 기존 기록만 소급할 수 있습니다.");
    }
    performanceStartedAt = from;
  }

  public LocalDate eligibleFrom() {
    var started = calculationStartedAt().atZone(ZoneId.of("Asia/Seoul"));
    return started.toLocalTime().isAfter(java.time.LocalTime.of(15, 30))
        ? started.toLocalDate().plusDays(1)
        : started.toLocalDate();
  }
}
