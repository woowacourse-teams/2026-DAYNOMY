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

  public PortfolioHoldingHistory(PortfolioHolding holding, PortfolioHoldingChangeType changeType) {
    this.portfolio = holding.getPortfolio();
    this.asset = holding.getAsset();
    this.changeType = changeType;
    this.quantity = holding.getQuantity();
    this.averagePurchasePrice = holding.getAveragePurchasePrice();
  }
}
