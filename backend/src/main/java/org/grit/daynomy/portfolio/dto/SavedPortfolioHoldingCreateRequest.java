package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SavedPortfolioHoldingCreateRequest(
    @NotNull @Positive Long assetId,
    @NotNull @Positive Long quantity,
    @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2)
        BigDecimal averagePurchasePrice) {}
