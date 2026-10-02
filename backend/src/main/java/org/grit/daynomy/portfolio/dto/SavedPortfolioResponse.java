package org.grit.daynomy.portfolio.dto;

import java.util.List;

public record SavedPortfolioResponse(List<SavedPortfolioHoldingResponse> holdings) {

  public static SavedPortfolioResponse empty() {
    return new SavedPortfolioResponse(List.of());
  }
}
