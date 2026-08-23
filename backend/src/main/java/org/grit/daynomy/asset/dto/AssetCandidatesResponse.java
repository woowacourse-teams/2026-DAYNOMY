package org.grit.daynomy.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AssetCandidatesResponse(
    @Schema(description = "데이터 기준일자", example = "20260821") String baseDate,
    @Schema(description = "코스닥 대표 종목 순위 목록") List<AssetCandidateResponse> rankings) {}
