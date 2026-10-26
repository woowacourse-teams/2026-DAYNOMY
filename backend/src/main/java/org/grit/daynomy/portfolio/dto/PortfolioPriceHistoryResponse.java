package org.grit.daynomy.portfolio.dto;

import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;

public record PortfolioPriceHistoryResponse(String status) {
  public static PortfolioPriceHistoryResponse from(PortfolioPriceHistoryService.Status status) {
    return new PortfolioPriceHistoryResponse(status.name());
  }
}
