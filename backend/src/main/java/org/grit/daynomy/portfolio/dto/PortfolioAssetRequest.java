package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PortfolioAssetRequest(
    @NotNull(message = "자산 ID는 필수입니다.") @Positive(message = "자산 ID는 양수여야 합니다.") Long assetId,
    @NotNull(message = "보유 비중은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "보유 비중은 0보다 커야 합니다.")
        @DecimalMax(value = "100", message = "보유 비중은 100 이하여야 합니다.")
        BigDecimal weight) {}
