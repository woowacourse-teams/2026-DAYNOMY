package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;

public record PortfolioPriceHistoryResponse(
    @Schema(
            description =
                "READY: 요청 범위 준비 완료 또는 수집할 대상 없음, PROCESSING: 종가 수집·스냅샷 갱신 중, FAILED: 준비 실패. FAILED 후 retry=true로 재시도",
            example = "PROCESSING",
            allowableValues = {"READY", "PROCESSING", "FAILED"})
        String status) {
  public static PortfolioPriceHistoryResponse from(PortfolioPriceHistoryService.Status status) {
    return new PortfolioPriceHistoryResponse(status.name());
  }
}
