package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentCalendarPortfolioAnalysisServiceTest {

  @Mock PortfolioRepository portfolioRepository;
  @Mock PortfolioHoldingRepository holdingRepository;
  @Mock StockDailyPriceRepository priceRepository;
  @Mock InvestmentEventRepository eventRepository;
  @Mock InvestmentEventReactionService reactionService;

  @Test
  void returnsNoPortfolioWhenMemberHasNoSavedPortfolio() {
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.empty());

    PortfolioEventAnalysis result = service().analyze(1L, event("selected", "2026-10-13", null));

    assertThat(result.status()).isEqualTo(PortfolioEventAnalysisStatus.NO_PORTFOLIO);
  }

  @Test
  void analyzesHistoricalEventsUsingCurrentEvaluationWeight() {
    Member member = Member.createGoogleMember("provider", "user@example.com", "사용자", null);
    ReflectionTestUtils.setField(member, "id", 1L);
    Portfolio portfolio = Portfolio.create(member);
    ReflectionTestUtils.setField(portfolio, "id", 10L);
    Asset asset = new Asset("테스트 종목", AssetCategory.STOCK, "000001");
    ReflectionTestUtils.setField(asset, "id", 100L);
    PortfolioHolding holding = new PortfolioHolding(portfolio, asset, 10L, new BigDecimal("90"));
    InvestmentEvent selected = event("selected", "2026-10-13", null);
    List<InvestmentEvent> histories =
        List.of(
            event("history-1", "2024-01-11", new BigDecimal("3.40")),
            event("history-2", "2024-02-13", new BigDecimal("3.40")),
            event("history-3", "2024-03-12", new BigDecimal("3.40")));

    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(List.of(holding));
    given(priceRepository.findFirstByAssetIdOrderByBaseDateDesc(100L))
        .willReturn(
            Optional.of(
                new StockDailyPrice(asset, LocalDate.parse("2026-10-05"), new BigDecimal("100"))));
    given(
            eventRepository
                .findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                    InvestmentEventType.US_CPI, selected.getAnnouncedAt()))
        .willReturn(histories);
    given(reactionService.calculate(histories.get(0), List.of(100L)))
        .willReturn(List.of(reaction(100L, "-2.00")));
    given(reactionService.calculate(histories.get(1), List.of(100L)))
        .willReturn(List.of(reaction(100L, "-1.00")));
    given(reactionService.calculate(histories.get(2), List.of(100L)))
        .willReturn(List.of(reaction(100L, "0.50")));

    PortfolioEventAnalysis result = service().analyze(1L, selected);

    assertThat(result.status()).isEqualTo(PortfolioEventAnalysisStatus.READY);
    assertThat(result.impactLevel()).isEqualTo(PortfolioEventImpactLevel.HIGH);
    assertThat(result.relatedAssetCount()).isEqualTo(1);
    assertThat(result.totalEvaluationAmount()).isEqualByComparingTo("1000");
    assertThat(result.statistics().getLast().medianReturnRate()).isEqualByComparingTo("-1.00");
  }

  private InvestmentCalendarPortfolioAnalysisService service() {
    return new InvestmentCalendarPortfolioAnalysisService(
        portfolioRepository, holdingRepository, priceRepository, eventRepository, reactionService);
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
