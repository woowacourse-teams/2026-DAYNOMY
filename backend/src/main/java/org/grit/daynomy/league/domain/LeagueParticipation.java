package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "league_participations")
public class LeagueParticipation extends BaseEntity implements LeagueParticipationValue {
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
}
