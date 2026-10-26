package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;

public record InvestmentCalendarEventResponse(
    @Schema(description = "투자 일정 ID", example = "1") Long id,
    @Schema(
            description =
                "일정 유형. US_CPI: 미국 소비자물가, KOREA_BASE_RATE: 한국 기준금리, CORPORATE_EARNINGS: 기업 실적")
        InvestmentEventType type,
    @Schema(description = "일정 제목") String title,
    @Schema(description = "발표 시각(UTC). 월 조회와 날짜 비교는 한국 시간으로 계산") Instant announcedAt,
    @Schema(description = "한국 시간 오늘부터 발표일까지의 일수. 과거는 음수, 당일은 0", example = "3") long daysUntil,
    @Schema(description = "발표일이 한국 시간 오늘보다 이후이면 true. 당일은 false") boolean upcoming,
    @Schema(description = "이전·실제 발표값과 변화 방향") InvestmentEventValueResponse value,
    @Schema(description = "일정 정보 출처") InvestmentEventSourceResponse source,
    @Schema(description = "해당 일정에 관련된 보유자산의 과거 반응 분석")
        PortfolioEventAnalysisResponse portfolioAnalysis) {

  public static InvestmentCalendarEventResponse of(
      InvestmentEvent event,
      PortfolioEventAnalysis analysis,
      LocalDate today,
      LocalDate eventDate) {
    return new InvestmentCalendarEventResponse(
        event.getId(),
        event.getType(),
        event.getTitle(),
        event.getAnnouncedAt(),
        ChronoUnit.DAYS.between(today, eventDate),
        eventDate.isAfter(today),
        InvestmentEventValueResponse.from(event),
        InvestmentEventSourceResponse.from(event),
        PortfolioEventAnalysisResponse.from(analysis));
  }
}
