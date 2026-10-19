package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.investmentcalendar.domain.AssetEventReaction;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysisStatus;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventImpactLevel;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentCalendarPortfolioAnalysisServiceTest {

  @Mock StockDailyPriceRepository priceRepository;
  @Mock InvestmentEventRepository eventRepository;
  @Mock InvestmentEventReactionService reactionService;

  @Test
  void returnsNoPortfolioWhenMemberHasNoSavedPortfolio() {
    InvestmentEvent selected = event("selected", "2026-10-13", null);
    PortfolioEventAnalysis result = service().analyze(List.of(), List.of(selected)).get(selected);

    assertThat(result.status()).isEqualTo(PortfolioEventAnalysisStatus.NO_PORTFOLIO);
  }

  @Test
  void analyzesHistoricalEventsUsingCurrentEvaluationWeight() {
    Asset asset = new Asset("테스트 종목", AssetCategory.STOCK, "000001");
    ReflectionTestUtils.setField(asset, "id", 100L);

    InvestmentEvent selected = event("selected", "2026-10-13", null);
    List<InvestmentEvent> histories =
        List.of(
            event("history-1", "2024-01-11", new BigDecimal("3.40")),
            event("history-2", "2024-02-13", new BigDecimal("3.40")),
            event("history-3", "2024-03-12", new BigDecimal("3.40")));

    given(priceRepository.findLatestTwoByAssetIds(List.of(100L)))
        .willReturn(
            List.of(
                new StockDailyPrice(asset, LocalDate.parse("2026-10-05"), new BigDecimal("100"))));
    given(
            eventRepository
                .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    InvestmentEventType.US_CPI, selected.getAnnouncedAt()))
        .willReturn(histories);
    given(reactionService.calculate(histories, List.of(100L)))
        .willReturn(
            Map.of(
                histories.get(0),
                List.of(reaction(100L, "-2.00")),
                histories.get(1),
                List.of(reaction(100L, "-1.00")),
                histories.get(2),
                List.of(reaction(100L, "0.50"))));

    PortfolioEventAnalysis result =
        service()
            .analyze(List.of(new InvestmentCalendarHolding(100L, "000001", 10L)), List.of(selected))
            .get(selected);

    assertThat(result.status()).isEqualTo(PortfolioEventAnalysisStatus.READY);
    assertThat(result.impactLevel()).isEqualTo(PortfolioEventImpactLevel.HIGH);
    assertThat(result.relatedAssetCount()).isEqualTo(1);
    assertThat(result.totalEvaluationAmount()).isEqualByComparingTo("1000");
    assertThat(result.statistics().getLast().medianReturnRate()).isEqualByComparingTo("-1.00");
  }

  @Test
  void analyzesGuestHoldingsWithoutSavedMemberPortfolio() {
    Asset asset = new Asset("테스트 종목", AssetCategory.STOCK, "000001");
    ReflectionTestUtils.setField(asset, "id", 100L);
    InvestmentEvent selected = event("selected", "2026-10-13", null);
    given(priceRepository.findLatestTwoByAssetIds(List.of(100L)))
        .willReturn(
            List.of(
                new StockDailyPrice(asset, LocalDate.parse("2026-10-05"), new BigDecimal("100"))));
    given(
            eventRepository
                .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    InvestmentEventType.US_CPI, selected.getAnnouncedAt()))
        .willReturn(List.of());

    PortfolioEventAnalysis result =
        service()
            .analyze(List.of(new InvestmentCalendarHolding(100L, "000001", 10L)), List.of(selected))
            .get(selected);

    assertThat(result.status()).isEqualTo(PortfolioEventAnalysisStatus.INSUFFICIENT_DATA);
    assertThat(result.relatedAssetCount()).isEqualTo(1);
    assertThat(result.totalEvaluationAmount()).isEqualByComparingTo("1000");
  }

  @Test
  void reusesMonthlyQueriesWithoutIncludingLaterAnnouncementsInEarlierSamples() {
    Asset asset = new Asset("테스트 종목", AssetCategory.STOCK, "000001");
    ReflectionTestUtils.setField(asset, "id", 100L);
    InvestmentEvent earlier = event("early", "2026-10-01", null);
    InvestmentEvent later = event("late", "2026-10-20", null);
    List<InvestmentEvent> histories =
        List.of(
            event("past", "2026-09-01", new BigDecimal("3.4")),
            event("between", "2026-10-10", new BigDecimal("3.4")));
    given(priceRepository.findLatestTwoByAssetIds(List.of(100L)))
        .willReturn(
            List.of(
                new StockDailyPrice(asset, LocalDate.of(2026, 10, 18), new BigDecimal("100")),
                new StockDailyPrice(asset, LocalDate.of(2026, 10, 19), new BigDecimal("110"))));
    given(
            eventRepository
                .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    InvestmentEventType.US_CPI, later.getAnnouncedAt()))
        .willReturn(histories);
    given(reactionService.calculate(histories, List.of(100L)))
        .willReturn(
            Map.of(
                histories.get(0),
                List.of(reaction(100L, "1")),
                histories.get(1),
                List.of(reaction(100L, "2"))));

    var result =
        service()
            .analyze(
                List.of(new InvestmentCalendarHolding(100L, "000001", 10L)),
                List.of(earlier, later));

    assertThat(result.get(earlier).statistics().getLast().sampleCount()).isEqualTo(1);
    assertThat(result.get(later).statistics().getLast().sampleCount()).isEqualTo(2);
    assertThat(result.get(earlier).totalEvaluationAmount()).isEqualByComparingTo("1100");
    then(priceRepository).should().findLatestTwoByAssetIds(List.of(100L));
    then(eventRepository)
        .should()
        .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
            InvestmentEventType.US_CPI, later.getAnnouncedAt());
    then(reactionService).should().calculate(histories, List.of(100L));
    then(priceRepository).shouldHaveNoMoreInteractions();
    then(eventRepository).shouldHaveNoMoreInteractions();
    then(reactionService).shouldHaveNoMoreInteractions();
  }

  @Test
  void keepsCorporateHistoriesAndEvaluationRestrictedToRelatedHolding() {
    Asset first = new Asset("첫 종목", AssetCategory.STOCK, "000001");
    Asset second = new Asset("둘째 종목", AssetCategory.STOCK, "000002");
    ReflectionTestUtils.setField(first, "id", 1L);
    ReflectionTestUtils.setField(second, "id", 2L);
    InvestmentEvent selected = corporateEvent("selected", "000001", null);
    InvestmentEvent related = corporateEvent("related", "000001", BigDecimal.ONE);
    InvestmentEvent unrelated = corporateEvent("unrelated", "000002", BigDecimal.ONE);
    given(priceRepository.findLatestTwoByAssetIds(List.of(1L, 2L)))
        .willReturn(
            List.of(
                new StockDailyPrice(first, LocalDate.of(2026, 10, 19), new BigDecimal("100")),
                new StockDailyPrice(second, LocalDate.of(2026, 10, 19), new BigDecimal("200"))));
    given(
            eventRepository
                .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    InvestmentEventType.CORPORATE_EARNINGS, selected.getAnnouncedAt()))
        .willReturn(List.of(related, unrelated));
    given(reactionService.calculate(List.of(related), List.of(1L, 2L)))
        .willReturn(Map.of(related, List.of(reaction(1L, "1"), reaction(2L, "20"))));

    var result =
        service()
            .analyze(
                List.of(
                    new InvestmentCalendarHolding(1L, "000001", 10L),
                    new InvestmentCalendarHolding(2L, "000002", 10L)),
                List.of(selected))
            .get(selected);

    assertThat(result.relatedAssetCount()).isEqualTo(1);
    assertThat(result.totalEvaluationAmount()).isEqualByComparingTo("1000");
    assertThat(result.statistics().stream().mapToInt(stat -> stat.sampleCount()).sum())
        .isEqualTo(1);
  }

  private InvestmentEvent corporateEvent(String key, String code, BigDecimal actual) {
    return new InvestmentEvent(
        InvestmentEventType.CORPORATE_EARNINGS,
        "실적 발표",
        LocalDate.of(actual == null ? 2026 : 2025, 10, 20)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant(),
        BigDecimal.ONE,
        actual,
        "원",
        "DART",
        "https://dart.fss.or.kr/",
        key,
        code);
  }

  private InvestmentCalendarPortfolioAnalysisService service() {
    return new InvestmentCalendarPortfolioAnalysisService(
        priceRepository, eventRepository, reactionService);
  }

  private InvestmentEvent event(String sourceKey, String date, BigDecimal actualValue) {
    return new InvestmentEvent(
        InvestmentEventType.US_CPI,
        "미국 소비자물가지수 발표",
        LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
        new BigDecimal("3.00"),
        actualValue,
        "%",
        "미국 노동통계국",
        "https://www.bls.gov/cpi/",
        sourceKey,
        null);
  }

  private AssetEventReaction reaction(Long assetId, String returnRate) {
    return new AssetEventReaction(
        assetId,
        LocalDate.parse("2024-01-10"),
        LocalDate.parse("2024-01-11"),
        new BigDecimal(returnRate));
  }
}
