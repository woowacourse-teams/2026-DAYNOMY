package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PortfolioMarketOpinionResponse(
    @Schema(description = "확인된 사실과 구분되는 전문가·시장 해석") String summary) {}
