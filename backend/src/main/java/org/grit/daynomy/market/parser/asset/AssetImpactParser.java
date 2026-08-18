package org.grit.daynomy.market.parser.asset;

import java.util.List;
import org.grit.daynomy.market.domain.asset.AssetImpact;
import org.grit.daynomy.market.domain.asset.Assets;
import org.grit.daynomy.market.entity.AssetImpactEntity;

public final class AssetImpactParser {

  private AssetImpactParser() {}

  public static Assets toDomain(List<AssetImpactEntity> entities) {
    return new Assets(entities.stream().map(AssetImpactParser::toDomain).toList());
  }

  public static AssetImpact toDomain(AssetImpactEntity entity) {
    return new AssetImpact(
        entity.getAsset(), entity.getDirection(), entity.getImpactLevel(), entity.getReason());
  }

  public static List<AssetImpactEntity> toEntity(Assets assets) {
    return assets.getValues().stream().map(AssetImpactParser::toEntity).toList();
  }

  public static AssetImpactEntity toEntity(AssetImpact assetImpact) {
    return new AssetImpactEntity(
        assetImpact.getAsset(),
        assetImpact.getDirection(),
        assetImpact.getImpactLevel(),
        assetImpact.getReason());
  }
}
