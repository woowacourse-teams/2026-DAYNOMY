package org.grit.daynomy.investmentcalendar.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GuestInvestmentCalendarHoldingRequest(
    @NotNull(message = "종목 ID를 입력해주세요.") @Positive(message = "종목 ID는 1 이상이어야 합니다.") Long assetId,
    @NotNull(message = "보유 수량을 입력해주세요.") @Positive(message = "보유 수량은 1 이상이어야 합니다.")
        Long quantity) {}
