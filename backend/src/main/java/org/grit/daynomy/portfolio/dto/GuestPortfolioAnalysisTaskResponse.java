package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record GuestPortfolioAnalysisTaskResponse(
    @Schema(description = "세션에 귀속된 작업 ID. 시작 전 또는 빈 자산 분석이면 null") UUID taskId,
    @Schema(description = "EMPTY: 시작 전, PROCESSING: 실행 중, READY: 핵심 결과 준비, FAILED: 핵심 결과 생성 실패")
        String status,
    @Schema(description = "검증된 점수·근거. 종합 설명 작성 중에도 조회 가능하며 준비 전에는 null")
        PortfolioAnalysisResponse analysis,
    @Schema(description = "종합 설명 상태: PENDING, READY, FAILED. 핵심 결과 준비 전에는 null")
        String summaryStatus,
    @Schema(description = "분석 또는 종합 설명 실패 코드. 실패 전에는 null") String failureCode,
    @Schema(description = "동일 작업을 명시적으로 재시도할 수 있는지 여부. 핵심 분석 실패 시에만 최대 1회 허용")
        boolean retryAllowed) {

  public static GuestPortfolioAnalysisTaskResponse emptyAnalysis() {
    return new GuestPortfolioAnalysisTaskResponse(
        null, "READY", PortfolioAnalysisResponse.empty(), "READY", null, false);
  }
}
