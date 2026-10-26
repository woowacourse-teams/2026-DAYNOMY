package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SharedPortfolioServiceTest {
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private StockPriceService stockPriceService;
  @Mock private InvestmentDecisionRepository decisionRepository;
  @Mock private MarketTradingDayRepository tradingDayRepository;
  @Mock private LeagueRankingCache rankingCache;
  @InjectMocks private SharedPortfolioService service;

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "true,true"})
  void loadsPricesOnceAtCollectedDateAndPreservesMissingValues(
      boolean collected, boolean pricesComplete) {
    var portfolio = SharedPortfolio.create(mock(Member.class));
    ReflectionTestUtils.setField(portfolio, "id", 2L);
    Asset first = asset(3L);
    Asset second = asset(4L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(2L))
        .willReturn(
            List.of(
                new SharedHolding(portfolio, first, 2, new BigDecimal("100")),
                new SharedHolding(portfolio, second, 3, new BigDecimal("200"))));
    LocalDate date = LocalDate.of(2026, 9, 22);
    var day = new MarketTradingDay(date, true);
    day.collected(Instant.parse("2026-09-22T08:00:00Z"));
    given(
            tradingDayRepository
                .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                    any(LocalDate.class)))
        .willReturn(collected ? Optional.of(day) : Optional.empty());
    if (collected) {
      var firstPrice = new StockDailyPrice(first, date, new BigDecimal("150"));
      given(stockPriceService.pricesAt(List.of(3L, 4L), date))
          .willReturn(
              pricesComplete
                  ? Map.of(
                      3L, firstPrice, 4L, new StockDailyPrice(second, date, new BigDecimal("250")))
                  : Map.of(3L, firstPrice));
    }

    var response = service.getMine(1L);

    assertThat(response.totalPurchaseAmount()).isEqualByComparingTo("800");
    assertThat(response.pricesComplete()).isEqualTo(pricesComplete);
    if (collected) {
      assertThat(response.holdings().getFirst().baseDate()).isEqualTo(date);
      assertThat(response.holdings().getFirst().evaluationAmount()).isEqualByComparingTo("300");
      assertThat(response.holdings().getFirst().returnRate()).isEqualByComparingTo("50");
      then(stockPriceService).should().pricesAt(List.of(3L, 4L), date);
      then(stockPriceService).shouldHaveNoMoreInteractions();
    } else {
      assertThat(response.holdings())
          .allSatisfy(
              holding -> {
                assertThat(holding.baseDate()).isNull();
                assertThat(holding.evaluationAmount()).isNull();
                assertThat(holding.returnRate()).isNull();
              });
      then(stockPriceService).should().pricesAt(List.of(3L, 4L), null);
    }
    if (pricesComplete) {
      assertThat(response.totalEvaluationAmount()).isEqualByComparingTo("1050");
      assertThat(response.totalReturnRate()).isEqualByComparingTo("31.25");
    } else {
      assertThat(response.holdings().getLast().evaluationAmount()).isNull();
      assertThat(response.totalEvaluationAmount()).isNull();
      assertThat(response.totalReturnRate()).isNull();
    }
  }

  private Asset asset(Long id) {
    Asset asset =
        Asset.listedStock(
            "종목 " + id, id.toString(), StockMarket.KOSPI, "KR0000000000", LocalDate.of(2026, 9, 1));
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }
}
