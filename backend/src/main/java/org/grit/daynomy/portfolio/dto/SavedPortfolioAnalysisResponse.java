package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SavedPortfolioAnalysisResponse(
    @Schema(description = "저장 결과 상태. FAILED/PROCESSING에서도 이전 분석을 포함할 수 있습니다.") Status status,
    @Schema(
            description =
                "최근 확인 성공 결과: UPDATED(갱신), UNCHANGED(동일 근거), UNCONFIRMED(근거 재확인 불가). 확인 전에는 null")
        String lastCheckOutcome,
    Long analysisId,
    @Schema(description = "저장된 분석의 자산·비중이 현재 회원 포트폴리오와 같은지 여부") boolean matchesCurrentPortfolio,
    @Schema(description = "실제 검색·분석을 성공한 최근 시각. 단순 조회·결과 재사용은 갱신하지 않습니다.") Instant lastSucceededAt,
    Instant lastFailedAt,
    String failureCode,
    String changeReason,
    @Schema(description = "저장된 분석. 아직 결과가 없으면 null") PortfolioAnalysisResponse analysis) {

  public enum Status {
    EMPTY,
    READY,
    PORTFOLIO_CHANGED,
    PROCESSING,
    RECHECK_REQUIRED,
    FAILED
  }

  public static SavedPortfolioAnalysisResponse empty() {
    return new SavedPortfolioAnalysisResponse(
        Status.EMPTY, null, null, false, null, null, null, null, null);
  }
}
