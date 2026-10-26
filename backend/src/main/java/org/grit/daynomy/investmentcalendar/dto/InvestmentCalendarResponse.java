package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record InvestmentCalendarResponse(
    @Schema(description = "조회 연도(한국 시간 기준)", example = "2026") int year,
    @Schema(description = "조회 월(한국 시간 기준)", example = "10") int month,
    @Schema(description = "발표 시각 오름차순 일정 목록. 일정이 없으면 빈 배열")
        List<InvestmentCalendarEventResponse> events) {}
