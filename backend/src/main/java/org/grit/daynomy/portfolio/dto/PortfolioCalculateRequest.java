package org.grit.daynomy.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PortfolioCalculateRequest(
    @NotEmpty(message = "보유자산을 한 개 이상 입력해주세요.")
        @Size(max = 50, message = "보유자산은 최대 50개까지 계산할 수 있습니다.")
        List<@NotNull(message = "보유자산 정보를 입력해주세요.") @Valid PortfolioHoldingRequest> holdings) {}
