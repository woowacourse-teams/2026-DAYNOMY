package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PortfolioHoldingRequest(
    @NotNull(message = "종목 ID를 입력해주세요.") @Positive(message = "종목 ID는 1 이상이어야 합니다.") Long assetId,
    @NotNull(message = "보유 수량을 입력해주세요.") @Positive(message = "보유 수량은 1 이상이어야 합니다.") Long quantity,
    @NotNull(message = "평균 매수가를 입력해주세요.")
        @DecimalMin(value = "0.01", message = "평균 매수가는 0보다 커야 합니다.")
        @Digits(integer = 15, fraction = 2, message = "평균 매수가는 정수 15자리, 소수 2자리 이하여야 합니다.")
        BigDecimal averagePurchasePrice) {}
