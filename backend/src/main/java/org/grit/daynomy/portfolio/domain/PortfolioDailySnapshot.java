package org.grit.daynomy.portfolio.domain;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "portfolio_daily_snapshots",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_portfolio_daily_snapshots_portfolio_date",
            columnNames = {"portfolio_id", "base_date"}))
public class PortfolioDailySnapshot extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private Portfolio portfolio;

  @Column(name = "base_date", nullable = false)
  private LocalDate baseDate;

  @Column(name = "total_purchase_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalPurchaseAmount;

  @Column(name = "total_evaluation_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalEvaluationAmount;

  @Column(name = "total_profit_loss", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalProfitLoss;

  @Column(name = "total_return_rate", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalReturnRate;

  @Column(name = "daily_profit_loss", precision = 19, scale = 2)
  private BigDecimal dailyProfitLoss;

  @Column(name = "daily_return_rate", precision = 10, scale = 2)
  private BigDecimal dailyReturnRate;

  public PortfolioDailySnapshot(
      Portfolio portfolio,
      LocalDate baseDate,
      BigDecimal totalPurchaseAmount,
      BigDecimal totalEvaluationAmount,
      BigDecimal totalProfitLoss,
      BigDecimal totalReturnRate,
      BigDecimal dailyProfitLoss,
      BigDecimal dailyReturnRate) {
    this.portfolio = portfolio;
    this.baseDate = baseDate;
    update(
        totalPurchaseAmount,
        totalEvaluationAmount,
        totalProfitLoss,
        totalReturnRate,
        dailyProfitLoss,
        dailyReturnRate);
  }

  public void update(
      BigDecimal totalPurchaseAmount,
      BigDecimal totalEvaluationAmount,
      BigDecimal totalProfitLoss,
      BigDecimal totalReturnRate,
      BigDecimal dailyProfitLoss,
      BigDecimal dailyReturnRate) {
    this.totalPurchaseAmount = totalPurchaseAmount;
    this.totalEvaluationAmount = totalEvaluationAmount;
    this.totalProfitLoss = totalProfitLoss;
    this.totalReturnRate = totalReturnRate;
    this.dailyProfitLoss = dailyProfitLoss;
    this.dailyReturnRate = dailyReturnRate;
  }
}
