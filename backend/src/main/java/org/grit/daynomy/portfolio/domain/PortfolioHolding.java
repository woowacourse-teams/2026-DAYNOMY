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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "portfolio_holdings",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_portfolio_holdings_portfolio_asset",
            columnNames = {"portfolio_id", "asset_id"}))
public class PortfolioHolding extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private Portfolio portfolio;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(name = "quantity", nullable = false)
  private long quantity;

  @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal averagePurchasePrice;

  public PortfolioHolding(
      Portfolio portfolio, Asset asset, long quantity, BigDecimal averagePurchasePrice) {
    this.portfolio = Objects.requireNonNull(portfolio);
    this.asset = Objects.requireNonNull(asset);
    change(quantity, averagePurchasePrice);
  }

  public void change(long quantity, BigDecimal averagePurchasePrice) {
    if (quantity <= 0 || averagePurchasePrice == null || averagePurchasePrice.signum() <= 0) {
      throw new IllegalArgumentException("보유 수량과 평균 매수가는 0보다 커야 합니다.");
    }
    this.quantity = quantity;
    this.averagePurchasePrice = averagePurchasePrice;
  }
}
