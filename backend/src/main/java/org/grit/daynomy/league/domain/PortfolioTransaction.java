package org.grit.daynomy.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.league.domain.LeagueTypes.HoldingPeriod;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "portfolio_transactions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_portfolio_transactions_request",
            columnNames = {"portfolio_id", "request_key"}))
public class PortfolioTransaction extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private SharedPortfolio portfolio;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(name = "request_key", nullable = false, length = 80)
  private String requestKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 10)
  private TransactionType transactionType;

  @Column(nullable = false)
  private long quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal fee;

  @Column(name = "traded_on", nullable = false)
  private LocalDate tradedOn;

  @Column(name = "decision_reason", nullable = false, length = 500)
  private String decisionReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "expected_holding_period", nullable = false, length = 30)
  private HoldingPeriod expectedHoldingPeriod;

  @Column(name = "expected_change", nullable = false, length = 300)
  private String expectedChange;

  @Column(name = "invalidation_condition", nullable = false, length = 300)
  private String invalidationCondition;

  @Column(name = "maximum_acceptable_loss_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal maximumAcceptableLossRate;

  @Column(name = "written_after_trade", nullable = false)
  private boolean writtenAfterTrade;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  public PortfolioTransaction(
      SharedPortfolio portfolio,
      Asset asset,
      String requestKey,
      TransactionType transactionType,
      long quantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      LocalDate tradedOn,
      String decisionReason,
      HoldingPeriod expectedHoldingPeriod,
      String expectedChange,
      String invalidationCondition,
      BigDecimal maximumAcceptableLossRate,
      boolean writtenAfterTrade) {
    this.portfolio = Objects.requireNonNull(portfolio);
    this.asset = Objects.requireNonNull(asset);
    this.requestKey = requireText(requestKey);
    this.transactionType = Objects.requireNonNull(transactionType);
    this.quantity = requirePositive(quantity);
    this.unitPrice = requirePositive(unitPrice);
    this.fee = requireNonNegative(fee);
    this.tradedOn = Objects.requireNonNull(tradedOn);
    this.decisionReason = requireText(decisionReason);
    this.expectedHoldingPeriod = Objects.requireNonNull(expectedHoldingPeriod);
    this.expectedChange = requireText(expectedChange);
    this.invalidationCondition = requireText(invalidationCondition);
    this.maximumAcceptableLossRate = requireRate(maximumAcceptableLossRate);
    this.writtenAfterTrade = writtenAfterTrade;
  }

  private String requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("투자 판단은 비어 있을 수 없습니다.");
    }
    return value.trim();
  }

  public boolean matchesDecision(
      String reason,
      HoldingPeriod period,
      String expected,
      String invalidation,
      BigDecimal lossRate) {
    return decisionReason.equals(reason.trim())
        && expectedHoldingPeriod == period
        && expectedChange.equals(expected.trim())
        && invalidationCondition.equals(invalidation.trim())
        && maximumAcceptableLossRate.compareTo(lossRate) == 0;
  }

  private long requirePositive(long value) {
    if (value <= 0) {
      throw new IllegalArgumentException("거래 수량은 0보다 커야 합니다.");
    }
    return value;
  }

  private BigDecimal requirePositive(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new IllegalArgumentException("거래 단가는 0보다 커야 합니다.");
    }
    return value;
  }

  private BigDecimal requireNonNegative(BigDecimal value) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException("수수료는 0보다 작을 수 없습니다.");
    }
    return value;
  }

  private BigDecimal requireRate(BigDecimal value) {
    if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("허용 손실률은 0에서 100 사이여야 합니다.");
    }
    return value;
  }
}
