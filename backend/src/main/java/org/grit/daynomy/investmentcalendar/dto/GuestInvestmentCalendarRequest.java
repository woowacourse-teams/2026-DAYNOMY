package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GuestInvestmentCalendarRequest(
    @Schema(description = "보유자산 목록. 빈 목록 허용, 최대 50개. 중복 assetId는 첫 수량을 사용하고 존재하지 않는 ID는 무시합니다.")
        @NotNull(message = "보유자산 목록을 입력해주세요.")
        @Size(max = 50, message = "보유자산은 최대 50개까지 조회할 수 있습니다.")
        List<@NotNull(message = "보유자산 정보를 입력해주세요.") @Valid GuestInvestmentCalendarHoldingRequest>
            holdings) {}
