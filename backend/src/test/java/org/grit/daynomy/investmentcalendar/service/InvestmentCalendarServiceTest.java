package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.investmentcalendar.domain.InvestmentCalendarScope;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;
import org.grit.daynomy.investmentcalendar.dto.InvestmentCalendarResponse;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentCalendarServiceTest {

  @Mock InvestmentEventRepository eventRepository;
  @Mock InvestmentCalendarPortfolioAnalysisService portfolioAnalysisService;
  @Mock PortfolioRepository portfolioRepository;
  @Mock PortfolioHoldingRepository holdingRepository;

  @Test
  void returnsEventsWithinRequestedSeoulMonth() {
    InvestmentEvent event =
        new InvestmentEvent(
            InvestmentEventType.US_CPI,
            "미국 소비자물가지수 발표",
            Instant.parse("2026-10-13T12:30:00Z"),
            new BigDecimal("2.90"),
            null,
            "%",
            "미국 노동통계국",
            "https://www.bls.gov/cpi/",
            "BLS-CPI-2026-10",
            null);
    ReflectionTestUtils.setField(event, "id", 1L);
    Instant from = Instant.parse("2026-09-30T15:00:00Z");
    Instant to = Instant.parse("2026-10-31T15:00:00Z");
    given(
            eventRepository
                .findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    from, to))
        .willReturn(List.of(event));
    given(portfolioAnalysisService.analyze(3L, event))
        .willReturn(PortfolioEventAnalysis.noPortfolio());

    InvestmentCalendarResponse response =
        new InvestmentCalendarService(
                eventRepository, portfolioAnalysisService, portfolioRepository, holdingRepository)
            .get(3L, YearMonth.of(2026, 10), InvestmentCalendarScope.ALL);

    assertThat(response.year()).isEqualTo(2026);
    assertThat(response.month()).isEqualTo(10);
    assertThat(response.events()).hasSize(1);
    assertThat(response.events().getFirst().id()).isEqualTo(1L);
  }

  @Test
  void hidesUnrelatedCorporateEventsFromPortfolioScope() {
    InvestmentEvent event =
        new InvestmentEvent(
            InvestmentEventType.CORPORATE_EARNINGS,
            "두산 3분기 실적 발표",
            Instant.parse("2026-10-21T00:00:00Z"),
            null,
            null,
            "백만원",
            "전자공시시스템(DART)",
            "https://dart.fss.or.kr/example",
            "DART-EARNINGS-000150-2026-09-30",
            "000150");
    Instant from = Instant.parse("2026-09-30T15:00:00Z");
    Instant to = Instant.parse("2026-10-31T15:00:00Z");
    given(
            eventRepository
                .findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    from, to))
        .willReturn(List.of(event));
    given(portfolioRepository.findByMemberId(3L)).willReturn(Optional.empty());

    InvestmentCalendarResponse response =
        new InvestmentCalendarService(
                eventRepository, portfolioAnalysisService, portfolioRepository, holdingRepository)
            .get(3L, YearMonth.of(2026, 10), InvestmentCalendarScope.PORTFOLIO);

    assertThat(response.events()).isEmpty();
  }
}
