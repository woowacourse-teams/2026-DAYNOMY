package org.grit.daynomy.portfolio.domain;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "portfolio_holding_histories")
public class PortfolioHoldingHistory extends BaseEntity {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private Portfolio portfolio;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_type", nullable = false, length = 20)
  private PortfolioHoldingChangeType changeType;

  @Column(nullable = false)
  private long quantity;

  @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal averagePurchasePrice;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "transaction_quantity")
  private Long transactionQuantity;

  @Column(name = "unit_price", precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "fee", nullable = false, precision = 19, scale = 2)
  private BigDecimal fee = BigDecimal.ZERO;

  @Column(name = "tax", nullable = false, precision = 19, scale = 2)
  private BigDecimal tax = BigDecimal.ZERO;

  public PortfolioHoldingHistory(PortfolioHolding holding, PortfolioHoldingChangeType changeType) {
    this(
        holding.getPortfolio(),
        holding.getAsset(),
        changeType,
        holding.getQuantity(),
        holding.getAveragePurchasePrice());
  }

  public PortfolioHoldingHistory(
      Portfolio portfolio,
      Asset asset,
      PortfolioHoldingChangeType changeType,
      long quantity,
      BigDecimal averagePurchasePrice) {
    this(
        portfolio,
        asset,
        changeType,
        quantity,
        averagePurchasePrice,
        LocalDate.now(SEOUL),
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  public PortfolioHoldingHistory(
      Portfolio portfolio,
      Asset asset,
      PortfolioHoldingChangeType changeType,
      long quantity,
      BigDecimal averagePurchasePrice,
      LocalDate effectiveDate,
      Long transactionQuantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      BigDecimal tax) {
    this.portfolio = portfolio;
    this.asset = asset;
    this.changeType = changeType;
    this.quantity = quantity;
    this.averagePurchasePrice = averagePurchasePrice;
    this.effectiveDate = effectiveDate;
    this.transactionQuantity = transactionQuantity;
    this.unitPrice = unitPrice;
    this.fee = fee;
    this.tax = tax;
  }

  public boolean isTransaction() {
    return changeType == PortfolioHoldingChangeType.BUY
        || changeType == PortfolioHoldingChangeType.SELL;
  }

  public boolean isBalanceAfter(LocalDate tradeDate) {
    return switch (changeType) {
      case OPENING_BALANCE, ADJUSTED, ADDED, UPDATED -> effectiveDate.isAfter(tradeDate);
      default -> false;
    };
  }

  public void updateTransaction(
      LocalDate tradeDate,
      long transactionQuantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      BigDecimal tax) {
    if (!isTransaction()) {
      throw new IllegalStateException("매수·매도 거래만 수정할 수 있습니다.");
    }
    this.effectiveDate = tradeDate;
    this.transactionQuantity = transactionQuantity;
    this.unitPrice = unitPrice;
    this.fee = fee;
    this.tax = tax;
  }
}
