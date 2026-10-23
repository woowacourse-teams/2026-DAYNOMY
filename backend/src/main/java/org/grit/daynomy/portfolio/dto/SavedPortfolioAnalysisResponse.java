package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SavedPortfolioAnalysisResponse(
    @Schema(
            description =
                "EMPTY: 저장 분석 없음, READY: 현재 자산과 일치, PORTFOLIO_CHANGED: 자산 변경, RECHECK_REQUIRED: 기준 변경·확인 후 24시간 경과·근거 부족 등으로 재확인 필요, FAILED: 최근 확인 실패, PROCESSING: 분석 중. PROCESSING, FAILED가 다른 상태에 우선하며 이전 분석을 포함할 수 있습니다.")
        Status status,
    @Schema(
            description =
                "최근 확인 성공 결과: UPDATED(갱신), UNCHANGED(동일 근거), UNCONFIRMED(근거 재확인 불가). 확인 전에는 null",
            nullable = true)
        String lastCheckOutcome,
    @Schema(description = "저장된 분석 ID. 분석이 없으면 null", nullable = true) Long analysisId,
    @Schema(description = "저장된 분석의 자산·비중이 현재 회원 포트폴리오와 같은지 여부") boolean matchesCurrentPortfolio,
    @Schema(
            description = "실제 검색·분석을 성공한 최근 시각(UTC). 단순 조회·결과 재사용은 갱신하지 않습니다. 성공 이력이 없으면 null",
            nullable = true)
        Instant lastSucceededAt,
    @Schema(description = "최근 분석 실패 시각(UTC). 실패 이력이 없으면 null", nullable = true)
        Instant lastFailedAt,
    @Schema(
            description = "최근 실패 오류 코드. 실패 이력이 없으면 null. 예상하지 못한 실패는 PORTFOLIO_ANALYSIS_FAILED",
            nullable = true)
        String failureCode,
    @Schema(description = "저장 분석의 생성·변경 사유. 분석이 없으면 null", nullable = true) String changeReason,
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
