package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;

public record PortfolioHoldingHistoryResponse(
    Long assetId,
    String assetCode,
    String name,
    PortfolioHoldingChangeType changeType,
    long quantity,
    BigDecimal averagePurchasePrice,
    Instant occurredAt) {

  public static PortfolioHoldingHistoryResponse from(PortfolioHoldingHistory history) {
    return new PortfolioHoldingHistoryResponse(
        history.getAsset().getId(),
        history.getAsset().getAssetCode(),
        history.getAsset().getName(),
        history.getChangeType(),
        history.getQuantity(),
        history.getAveragePurchasePrice(),
        history.getCreatedAt());
  }
}
