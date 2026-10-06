package org.grit.daynomy.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "portfolio_daily_returns",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_portfolio_daily_returns_date_version",
            columnNames = {"portfolio_id", "base_date", "calculation_version"}))
public class PortfolioDailyReturn extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private SharedPortfolio portfolio;

  @Column(name = "base_date", nullable = false)
  private LocalDate baseDate;

  @Column(name = "daily_return_rate", precision = 10, scale = 4)
  private BigDecimal dailyReturnRate;

  @Column(name = "starting_evaluation_amount", precision = 19, scale = 2)
  private BigDecimal startingEvaluationAmount;

  @Column(name = "ending_evaluation_amount", precision = 19, scale = 2)
  private BigDecimal endingEvaluationAmount;

  @Column(name = "max_holding_weight", precision = 10, scale = 2)
  private BigDecimal maxHoldingWeight;

  @Column(nullable = false)
  private boolean eligible;

  @Column(name = "ineligible_reason", length = 40)
  private String ineligibleReason;

  @Column(name = "calculation_version", nullable = false)
  private int calculationVersion = 1;

  @Column(name = "calculated_at", nullable = false)
  private Instant calculatedAt;

  public PortfolioDailyReturn(SharedPortfolio portfolio, LocalDate baseDate) {
    this.portfolio = Objects.requireNonNull(portfolio);
    this.baseDate = Objects.requireNonNull(baseDate);
  }

  public void complete(
      BigDecimal dailyReturnRate,
      BigDecimal startingEvaluationAmount,
      BigDecimal endingEvaluationAmount,
      BigDecimal maxHoldingWeight,
      Instant calculatedAt) {
    this.dailyReturnRate = dailyReturnRate;
    this.startingEvaluationAmount = startingEvaluationAmount;
    this.endingEvaluationAmount = endingEvaluationAmount;
    this.maxHoldingWeight = maxHoldingWeight;
    this.eligible = true;
    this.ineligibleReason = null;
    this.calculatedAt = calculatedAt;
  }

  public void exclude(String reason, Instant calculatedAt) {
    this.dailyReturnRate = null;
    this.startingEvaluationAmount = null;
    this.endingEvaluationAmount = null;
    this.maxHoldingWeight = null;
    this.eligible = false;
    this.ineligibleReason = reason;
    this.calculatedAt = calculatedAt;
  }
}
