package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
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
    name = "shared_holdings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "asset_id"}))
public class SharedHolding extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private SharedPortfolio portfolio;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(nullable = false)
  private long quantity;

  @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal averagePurchasePrice;

  @Column(nullable = false)
  private boolean hidden;

  @Column(nullable = false, length = 500)
  private String reason = "";

  public SharedHolding(SharedPortfolio portfolio, Asset asset, long quantity, BigDecimal price) {
    this.portfolio = Objects.requireNonNull(portfolio);
    this.asset = Objects.requireNonNull(asset);
    change(quantity, price);
  }

  public boolean change(long quantity, BigDecimal price) {
    if (quantity < 1
        || quantity > 1_000_000_000L
        || price == null
        || price.signum() <= 0
        || price.compareTo(new BigDecimal("1000000000")) > 0
        || price.scale() > 2) {
      throw new IllegalArgumentException("수량과 평균 매수가를 확인해 주세요.");
    }
    if (this.quantity == quantity && price.compareTo(this.averagePurchasePrice) == 0) {
      return false;
    }
    this.quantity = quantity;
    this.averagePurchasePrice = price;
    return true;
  }

  public void describe(boolean hidden, String reason) {
    String text = reason == null ? "" : reason.trim();
    if (text.length() > 500) throw new IllegalArgumentException("판단 근거는 500자 이하로 작성해 주세요.");
    this.hidden = hidden;
    this.reason = text;
  }

  public void changeVisibility(boolean hidden) {
    this.hidden = hidden;
  }
}
