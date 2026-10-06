package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "shared_holding_histories")
public class SharedHoldingHistory extends BaseEntity {
  public enum ChangeType {
    ADDED,
    UPDATED,
    REMOVED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private SharedPortfolio portfolio;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_type", nullable = false, length = 20)
  private ChangeType changeType;

  @Column(nullable = false)
  private long quantity;

  @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal averagePurchasePrice;

  @Column(nullable = false)
  private boolean hidden;

  @Column(nullable = false, length = 500)
  private String reason;

  public SharedHoldingHistory(SharedHolding holding, ChangeType changeType) {
    portfolio = holding.getPortfolio();
    asset = holding.getAsset();
    quantity = holding.getQuantity();
    averagePurchasePrice = holding.getAveragePurchasePrice();
    hidden = holding.isHidden();
    reason = holding.getReason();
    this.changeType = changeType;
  }
}
