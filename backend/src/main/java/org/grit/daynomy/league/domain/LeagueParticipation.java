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
    return !close.isBefore(startedAt) && (endedAt == null || close.isBefore(endedAt));
  }

  public LocalDate eligibleFrom() {
    var started = startedAt.atZone(ZoneId.of("Asia/Seoul"));
    return started.toLocalTime().isAfter(java.time.LocalTime.of(15, 30))
        ? started.toLocalDate().plusDays(1)
        : started.toLocalDate();
  }
}
