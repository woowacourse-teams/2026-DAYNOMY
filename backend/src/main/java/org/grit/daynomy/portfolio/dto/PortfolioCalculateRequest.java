package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PortfolioCalculateRequest(
    @NotEmpty(message = "보유자산을 한 개 이상 입력해주세요.")
        @Size(max = 50, message = "보유자산은 최대 50개까지 계산할 수 있습니다.")
        @Schema(description = "계산할 보유자산 목록. 1~50개, 종목 ID 중복 불가. 저장된 포트폴리오에는 반영하지 않음")
        List<@NotNull(message = "보유자산 정보를 입력해주세요.") @Valid PortfolioHoldingRequest> holdings) {}
