package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SavedPortfolioHoldingUpdateRequest(
    @NotNull @Positive Long quantity,
    @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 15, fraction = 2)
        @Schema(description = "원화(KRW) 기준 평균 매입가")
        BigDecimal averagePurchasePrice) {}
