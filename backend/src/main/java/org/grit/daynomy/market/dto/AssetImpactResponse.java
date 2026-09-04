package org.grit.daynomy.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

public record AssetImpactResponse(
    @Schema(description = "영향을 받는 자산군", example = "STOCK") AssetCategory category,
    @Schema(description = "영향 방향", example = "POSITIVE") ImpactDirection direction,
    @Schema(description = "영향 수준", example = "HIGH") ImpactLevel impactLevel,
    @Schema(description = "판단 근거", example = "할인율 하락 기대가 주식 밸류에이션에 긍정적입니다.") String reason) {}
