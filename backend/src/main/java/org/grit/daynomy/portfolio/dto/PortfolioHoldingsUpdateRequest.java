package org.grit.daynomy.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record PortfolioHoldingsUpdateRequest(
    @NotEmpty @Size(max = 200) List<@NotNull @Valid Holding> holdings) {
  public record Holding(
      @NotNull @Positive Long assetId,
      @NotNull @Positive Long quantity,
      @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2)
          BigDecimal averagePurchasePrice,
      @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
          BigDecimal nativeAveragePurchasePrice) {}
}
