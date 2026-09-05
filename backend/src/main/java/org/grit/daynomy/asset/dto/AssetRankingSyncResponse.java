package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AssetRankingSyncResponse(
    @Schema(description = "저장된 코스닥 대표 종목 순위 개수", example = "150") int savedCount) {}
