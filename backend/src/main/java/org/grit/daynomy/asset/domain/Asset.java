package org.grit.daynomy.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    name = "assets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_assets_category_asset_code",
            columnNames = {"category", "asset_code"}))
public class Asset extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private AssetCategory category;

  @Column(name = "asset_code", nullable = false, length = 50)
  private String assetCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "market", length = 20)
  private StockMarket market;

  @Column(name = "isin_code", length = 12)
  private String isinCode;

  @Column(name = "listed", nullable = false)
  private boolean listed;

  @Column(name = "stock_base_date")
  private LocalDate stockBaseDate;

  public Asset(String name, AssetCategory category, String assetCode) {
    this.name = name;
    this.category = category;
    this.assetCode = assetCode;
  }

  public static Asset listedStock(
      String name, String assetCode, StockMarket market, String isinCode, LocalDate baseDate) {
    Asset asset = new Asset(name, AssetCategory.STOCK, assetCode);
    asset.market = market;
    asset.isinCode = isinCode;
    asset.listed = true;
    asset.stockBaseDate = baseDate;
    return asset;
  }

  public boolean synchronizeStock(
      String name, StockMarket market, String isinCode, LocalDate baseDate) {
    boolean changed =
        !Objects.equals(this.name, name)
            || this.market != market
            || !Objects.equals(this.isinCode, isinCode)
            || !this.listed
            || !Objects.equals(this.stockBaseDate, baseDate);

    this.name = name;
    this.market = market;
    this.isinCode = isinCode;
    this.listed = true;
    this.stockBaseDate = baseDate;
    return changed;
  }

  public boolean delist() {
    if (!listed) {
      return false;
    }
    listed = false;
    return true;
  }
}
