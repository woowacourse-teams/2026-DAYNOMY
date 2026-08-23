package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AssetCandidateResponse(
    @Schema(description = "순위", example = "1") int rank,
    @Schema(description = "종목 코드", example = "247540") String code,
    @Schema(description = "종목명", example = "에코프로비엠") String name) {}
