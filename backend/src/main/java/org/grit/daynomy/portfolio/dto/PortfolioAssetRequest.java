package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PortfolioAssetRequest(
    @Schema(description = "종목명. 앞뒤 공백을 제거하며 공백만 입력할 수 없습니다. 최대 100자", example = "삼성전자")
        @NotBlank(message = "종목명은 필수입니다.")
        @Size(max = 100, message = "종목명은 100자 이하여야 합니다.")
        String assetName,
    @Schema(description = "전체 자산 대비 보유 비중(%). 0 초과·100 이하", example = "100")
        @NotNull(message = "보유 비중은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "보유 비중은 0보다 커야 합니다.")
        @DecimalMax(value = "100", message = "보유 비중은 100 이하여야 합니다.")
        BigDecimal weight,
    @Schema(
            description = "보유 수량. 생략 또는 null 허용, 입력 시 1 이상. 회원 분석은 요청 수량 대신 서버에 저장된 수량을 사용합니다.",
            example = "10",
            minimum = "1",
            nullable = true)
        @jakarta.validation.constraints.Positive(message = "수량은 0보다 커야 합니다.")
        Long quantity) {

  public PortfolioAssetRequest(String assetName, BigDecimal weight) {
    this(assetName, weight, null);
  }

  public PortfolioAssetRequest {
    assetName = assetName == null ? null : assetName.strip();
  }
}
