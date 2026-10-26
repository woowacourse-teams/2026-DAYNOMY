package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GuestInvestmentCalendarHoldingRequest(
    @Schema(description = "종목 ID(1 이상)", example = "1", minimum = "1")
        @NotNull(message = "종목 ID를 입력해주세요.")
        @Positive(message = "종목 ID는 1 이상이어야 합니다.")
        Long assetId,
    @Schema(description = "보유 수량(1 이상)", example = "10", minimum = "1")
        @NotNull(message = "보유 수량을 입력해주세요.")
        @Positive(message = "보유 수량은 1 이상이어야 합니다.")
        Long quantity) {}
