package org.grit.daynomy.asset.domain;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(
    name = "stock_daily_prices",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_stock_daily_prices_asset_date",
            columnNames = {"asset_id", "base_date"}))
public class StockDailyPrice extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(name = "base_date", nullable = false)
  private LocalDate baseDate;

  @Column(name = "close_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal closePrice;

  public StockDailyPrice(Asset asset, LocalDate baseDate, BigDecimal closePrice) {
    this.asset = Objects.requireNonNull(asset);
    this.baseDate = Objects.requireNonNull(baseDate);
    this.closePrice = requirePositive(closePrice);
  }

  public boolean synchronize(BigDecimal closePrice) {
    BigDecimal validatedClosePrice = requirePositive(closePrice);
    if (this.closePrice.compareTo(validatedClosePrice) == 0) {
      return false;
    }
    this.closePrice = validatedClosePrice;
    return true;
  }

  private BigDecimal requirePositive(BigDecimal price) {
    if (price == null || price.signum() <= 0) {
      throw new IllegalArgumentException("종가는 0보다 커야 합니다.");
    }
    return price;
  }
}
