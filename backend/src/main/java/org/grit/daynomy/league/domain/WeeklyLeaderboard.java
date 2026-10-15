package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "weekly_leaderboards",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"week_start", "league_type", "calculation_version"}))
public class WeeklyLeaderboard extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "week_start", nullable = false)
  private LocalDate weekStart;

  @Enumerated(EnumType.STRING)
  @Column(name = "league_type", nullable = false, length = 20)
  private LeagueType leagueType;

  @Column(name = "calculation_version", nullable = false)
  private int calculationVersion;

  @Column(name = "as_of_date", nullable = false)
  private LocalDate asOfDate;

  @Column(name = "finalized_at", nullable = false)
  private Instant finalizedAt;

  @Column(name = "change_reason", nullable = false, length = 80)
  private String changeReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Entry> entries;

  public WeeklyLeaderboard(
      LocalDate weekStart,
      LeagueType leagueType,
      int version,
      LocalDate asOfDate,
      String changeReason,
      List<Entry> entries) {
    this.weekStart = weekStart;
    this.leagueType = leagueType;
    this.calculationVersion = version;
    this.asOfDate = asOfDate;
    this.finalizedAt = Instant.now();
    this.changeReason = changeReason;
    this.entries = List.copyOf(entries);
  }

  public record Entry(
      int rank,
      Long profileId,
      BigDecimal weeklyReturnRate,
      BigDecimal eightWeekReturnRate,
      BigDecimal threeWeekReturnRate,
      BigDecimal cumulativeReturnRate,
      BigDecimal maxDrawdownRate,
      BigDecimal volatilityRate,
      BigDecimal maxHoldingWeight,
      BigDecimal currentHoldingWeight,
      int consecutiveRecordedWeeks,
      int decisionCount,
      BigDecimal reviewCompletionRate,
      LocalDate trackingStartedOn) {}
}
