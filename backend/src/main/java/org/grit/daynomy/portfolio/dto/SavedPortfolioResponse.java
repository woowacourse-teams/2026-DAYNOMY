package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record SavedPortfolioResponse(
    @Schema(description = "현재 보유자산 목록. 포트폴리오가 없거나 보유자산이 없으면 빈 배열")
        List<SavedPortfolioHoldingResponse> holdings,
    @Schema(description = "거래 원장 기준 누적 실현손익(KRW). 포트폴리오가 없으면 0", example = "0.00")
        BigDecimal totalRealizedProfitLoss) {

  public static SavedPortfolioResponse empty() {
    return new SavedPortfolioResponse(List.of(), BigDecimal.ZERO.setScale(2));
  }
}
