package org.grit.daynomy.market.domain.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class AssetImpact {

  @Enumerated(EnumType.STRING)
  @Column(name = "asset", nullable = false)
  private Asset asset;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false)
  private ImpactDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "impact_level", nullable = false)
  private ImpactLevel impactLevel;

  @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
  private String reason;

  public AssetImpact(
      Asset asset, ImpactDirection direction, ImpactLevel impactLevel, String reason) {
    this.asset = asset;
    this.direction = direction;
    this.impactLevel = impactLevel;
    this.reason = reason;
  }
}
