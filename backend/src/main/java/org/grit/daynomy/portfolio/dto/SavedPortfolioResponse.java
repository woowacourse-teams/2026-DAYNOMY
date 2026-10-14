package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record SavedPortfolioResponse(
    List<SavedPortfolioHoldingResponse> holdings, BigDecimal totalRealizedProfitLoss) {

  public static SavedPortfolioResponse empty() {
    return new SavedPortfolioResponse(List.of(), BigDecimal.ZERO.setScale(2));
  }
}
