package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.Test;

class PortfolioLedgerCalculatorTest {

  private final PortfolioLedgerCalculator calculator = new PortfolioLedgerCalculator();

  @Test
  void replayCalculatesWeightedAverageAndRealizedProfitLoss() {
    Asset asset = asset(1L);

    var result =
        calculator.replay(
            List.of(
                transaction(asset, PortfolioHoldingChangeType.BUY, 10L, "10000", "100", "0"),
                transaction(asset, PortfolioHoldingChangeType.BUY, 10L, "12000", "100", "0"),
                transaction(asset, PortfolioHoldingChangeType.SELL, 5L, "13000", "50", "100")));

    assertThat(result.positions().get(1L).quantity()).isEqualTo(15L);
    assertThat(result.positions().get(1L).averagePurchasePrice()).isEqualByComparingTo("11010.00");
    assertThat(result.realizedProfitLoss()).isEqualByComparingTo("9800.00");
  }

  @Test
  void replayRejectsSellingMoreThanOwnedAtThatPointInTime() {
    Asset asset = asset(1L);

    assertThatThrownBy(
            () ->
                calculator.replay(
                    List.of(
                        transaction(asset, PortfolioHoldingChangeType.BUY, 2L, "10000", "0", "0"),
                        transaction(
                            asset, PortfolioHoldingChangeType.SELL, 3L, "12000", "0", "0"))))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY);
  }

  @Test
  void usdBuyAndSellUseTheirOwnExchangeRatesForKrwCostAndRealizedProfit() {
    var asset =
        org.grit.daynomy.asset.domain.Asset.listedStock(
            "Tesla",
            "TSLA.US",
            org.grit.daynomy.asset.domain.StockMarket.NASDAQ,
            null,
            LocalDate.of(2020, 1, 1));
    org.springframework.test.util.ReflectionTestUtils.setField(asset, "id", 99L);
    var buy =
        new PortfolioHoldingHistory(
            null,
            asset,
            PortfolioHoldingChangeType.BUY,
            0,
            BigDecimal.ZERO,
            LocalDate.of(2020, 1, 2),
            2L,
            new BigDecimal("100.1234"),
            new BigDecimal("1"),
            BigDecimal.ZERO);
    buy.applyExchangeRate(new BigDecimal("1300"));
    var sell =
        new PortfolioHoldingHistory(
            null,
            asset,
            PortfolioHoldingChangeType.SELL,
            2,
            BigDecimal.ZERO,
            LocalDate.of(2020, 1, 3),
            1L,
            new BigDecimal("110"),
            new BigDecimal("1"),
            new BigDecimal("0.5"));
    sell.applyExchangeRate(new BigDecimal("1400"));
    var result = calculator.replay(List.of(buy, sell));
    assertThat(result.positions().get(99L).averagePurchasePrice())
        .isEqualByComparingTo("130810.42");
    assertThat(result.positions().get(99L).quantity()).isEqualTo(1);
    assertThat(result.realizedProfitLoss()).isEqualByComparingTo("21089.58");
    assertThatThrownBy(() -> buy.applyExchangeRate(null)).isInstanceOf(BusinessException.class);
  }

  private PortfolioHoldingHistory transaction(
      Asset asset,
      PortfolioHoldingChangeType type,
      long quantity,
      String unitPrice,
      String fee,
      String tax) {
    PortfolioHoldingHistory history = mock(PortfolioHoldingHistory.class);
    given(history.getAsset()).willReturn(asset);
    given(history.getChangeType()).willReturn(type);
    given(history.getTransactionQuantity()).willReturn(quantity);
    given(history.getUnitPrice()).willReturn(new BigDecimal(unitPrice));
    given(history.getExchangeRate()).willReturn(BigDecimal.ONE);
    given(history.getFee()).willReturn(new BigDecimal(fee));
    given(history.getTax()).willReturn(new BigDecimal(tax));
    given(history.getEffectiveDate()).willReturn(LocalDate.of(2026, 9, 30));
    return history;
  }

  private Asset asset(Long id) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    return asset;
  }
}
