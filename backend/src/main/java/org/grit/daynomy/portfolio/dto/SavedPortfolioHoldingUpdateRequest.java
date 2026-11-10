package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SavedPortfolioHoldingUpdateRequest(
    @NotNull @Positive @Schema(description = "조정 후 보유 수량(주). 1 이상의 정수", example = "10")
        Long quantity,
    @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 15, fraction = 2)
        @Schema(
            description = "조정 후 원화(KRW) 기준 평균 매입가. 0.01 이상, 정수 15자리·소수 2자리 이하",
            example = "70000.00")
        BigDecimal averagePurchasePrice) {}
