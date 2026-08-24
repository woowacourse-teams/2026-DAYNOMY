package org.grit.daynomy.market.domain.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.AssetCategory;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class AssetImpact {

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private AssetCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false)
  private ImpactDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "impact_level", nullable = false)
  private ImpactLevel impactLevel;

  @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
  private String reason;

  public AssetImpact(
      AssetCategory category, ImpactDirection direction, ImpactLevel impactLevel, String reason) {
    this.category = category;
    this.direction = direction;
    this.impactLevel = impactLevel;
    this.reason = reason;
  }
}
