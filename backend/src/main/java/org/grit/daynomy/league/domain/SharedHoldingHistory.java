package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  public SharedHoldingHistory(SharedHolding holding, ChangeType changeType) {
    portfolio = holding.getPortfolio();
    asset = holding.getAsset();
    quantity = holding.getQuantity();
    averagePurchasePrice = holding.getAveragePurchasePrice();
    this.changeType = changeType;
  }

  /** 생성 시각·ID 오름차순 이력에서 종목별 마지막 보유 상태를 복원한다. */
  public static List<SharedHoldingHistory> reconstructHoldings(
      List<SharedHoldingHistory> histories) {
    Map<Long, SharedHoldingHistory> holdings = new LinkedHashMap<>();
    for (SharedHoldingHistory history : histories) {
      Long assetId = history.getAsset().getId();
      if (history.getChangeType() == ChangeType.REMOVED) {
        holdings.remove(assetId);
      } else {
        holdings.put(assetId, history);
      }
    }
    return List.copyOf(holdings.values());
  }
}
