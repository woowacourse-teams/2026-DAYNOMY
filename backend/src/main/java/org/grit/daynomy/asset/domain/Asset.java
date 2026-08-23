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

  public Asset(String name, AssetCategory category, String assetCode) {
    this.name = name;
    this.category = category;
    this.assetCode = assetCode;
  }
}
