package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record PortfolioPerformanceResponse(
    @Schema(
            description =
                "READY: 조회 기간의 과거 스냅샷이 2개 이상, INSUFFICIENT_DATA: 2개 미만. 오늘 값은 개수에 포함하지 않음",
            example = "READY")
        PortfolioPerformanceStatus status,
    @Schema(
            description = "데이터 부족 사유. INSUFFICIENT_DATA이면 SNAPSHOT_DATA_INSUFFICIENT, READY이면 null",
            nullable = true)
        PortfolioPerformanceUnavailableReason reason,
    @Schema(
            description = "조회 기간의 가장 최근 과거 스냅샷 기준일. 스냅샷이 없으면 null",
            example = "2026-10-22",
            nullable = true)
        LocalDate baseDate,
    @Schema(
            description = "조회 기간의 두 번째로 최근 과거 스냅샷 기준일. 2개 미만이면 null",
            example = "2026-10-21",
            nullable = true)
        LocalDate previousBaseDate,
    @Schema(description = "오늘을 제외한 과거 스냅샷 목록. 날짜 오름차순, 데이터가 없으면 빈 배열")
        List<PortfolioPerformancePointResponse> points,
    @Schema(
            description =
                "서울 기준 오늘이 조회 기간에 포함될 때 계산한 현재 값. 포트폴리오가 없거나 기간에 오늘이 없거나 가격 상태가 COMPLETE가 아니면 null. 보유자산이 없으면 금액 0",
            types = {"object", "null"},
            schemaResolution = Schema.SchemaResolution.INLINE)
        PortfolioCurrentPerformanceResponse currentPoint) {}
