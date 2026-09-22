package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PortfolioAssetRequest(
    @NotBlank(message = "종목명은 필수입니다.") @Size(max = 100, message = "종목명은 100자 이하여야 합니다.")
        String assetName,
    @NotNull(message = "보유 비중은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "보유 비중은 0보다 커야 합니다.")
        @DecimalMax(value = "100", message = "보유 비중은 100 이하여야 합니다.")
        BigDecimal weight) {

  public PortfolioAssetRequest {
    assetName = assetName == null ? null : assetName.strip();
  }
}
