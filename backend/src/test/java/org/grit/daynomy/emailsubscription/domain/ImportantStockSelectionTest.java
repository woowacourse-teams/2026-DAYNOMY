package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection.HoldingPrice;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection.Stock;
import org.junit.jupiter.api.Test;

class ImportantStockSelectionTest {

  private final LocalDate baseDate = LocalDate.of(2026, 10, 19);
  private final LocalDate previousDate = LocalDate.of(2026, 10, 16);

  @Test
  void selectsBothRisesAndFallsAtExactlyFivePercent() {
    var result = select(holding(1L, 1, "105", "100"), holding(2L, 1, "95", "100"));

    assertThat(result.stocks()).extracting(Stock::assetId).containsExactly(1L, 2L);
    assertThat(result.stocks().get(0).dailyReturnRate()).isEqualByComparingTo("5");
    assertThat(result.stocks().get(1).dailyReturnRate()).isEqualByComparingTo("-5");
  }

  @Test
  void usesPreviousClosingWeightsToSelectExactlyOnePercentagePointOfContribution() {
    var result = select(holding(1L, 1, "102", "100"), holding(2L, 1, "100", "100"));

    assertThat(result.stocks()).hasSize(1);
    Stock stock = result.stocks().getFirst();
    assertThat(stock.assetId()).isEqualTo(1L);
    assertThat(stock.weight()).isEqualByComparingTo("50");
    assertThat(stock.dailyReturnRate()).isEqualByComparingTo("2");
    assertThat(stock.contribution()).isEqualByComparingTo("1");
  }

  @Test
  void doesNotRoundUpSubthresholdReturnsOrContributions() {
    var result =
        select(
            holding(1L, 1, "104.99999999999", "100"),
            holding(2L, 1, "100", "900"),
            holding(3L, 1, "9099.9999999999", "9000"));

    // 2번의 큰 하락만 해당한다. 1번은 5% 미만, 3번의 기여도는 1%p 미만이다.
    assertThat(result.stocks()).extracting(Stock::assetId).containsExactly(2L);
  }

  @Test
  void limitsToThreeOrderedByAbsoluteContributionThenWeight() {
    var result =
        select(
            holding(4L, 1, "110", "100"),
            holding(3L, 1, "90", "100"),
            holding(2L, 1, "190", "200"),
            holding(1L, 1, "80", "100"));

    assertThat(result.stocks()).extracting(Stock::assetId).containsExactly(1L, 2L, 3L);
    assertThat(result.stocks().getFirst().contribution()).isEqualByComparingTo("-4");
  }

  @Test
  void doesNotFillTheLimitWhenNothingIsImportant() {
    var result = select(holding(1L, 1, "100.1", "100"));

    assertThat(result.stocks()).isEmpty();
    assertThat(result.completePrices()).isTrue();
    assertThat(result.baseDate()).isEqualTo(baseDate);
    assertThat(result.previousBaseDate()).isEqualTo(previousDate);
  }

  @Test
  void holdingQuantityDeterminesPersonalImpactRatherThanTheStockReturnAlone() {
    var result = select(holding(1L, 1, "110", "100"), holding(2L, 10, "102", "100"));

    assertThat(result.stocks()).extracting(Stock::assetId).containsExactly(2L, 1L);
    assertThat(result.stocks().getFirst().dailyReturnRate()).isEqualByComparingTo("2");
    assertThat(result.stocks().getFirst().contribution()).isEqualByComparingTo("1.8181818182");
  }

  @Test
  void missingPricesDisableWeightsForAllHoldingsButAllowValidStockReturns() {
    var result =
        select(
            holding(1L, 1, "102", "100"),
            holding(2L, 1, "95", "100"),
            new HoldingPrice(3L, 1, null, new BigDecimal("100")));

    assertThat(result.completePrices()).isFalse();
    assertThat(result.stocks()).extracting(Stock::assetId).containsExactly(2L);
    assertThat(result.stocks().getFirst().weight()).isNull();
    assertThat(result.stocks().getFirst().contribution()).isNull();
  }

  @Test
  void aMissingPreviousPriceIsNotAZeroPriceOrAnUnchangedStock() {
    var result = select(new HoldingPrice(1L, 10, new BigDecimal("100"), null));

    assertThat(result.stocks()).isEmpty();
    assertThat(result.completePrices()).isFalse();
  }

  @Test
  void preservesAssetIdentityForDifferentShareClassesWithEqualPriority() {
    var result = select(holding(2L, 1, "105", "100"), holding(1L, 1, "105", "100"));

    assertThat(result.stocks()).extracting(Stock::assetId).containsExactly(1L, 2L);
  }

  private ImportantStockSelection select(HoldingPrice... holdings) {
    return ImportantStockSelection.select(baseDate, previousDate, List.of(holdings));
  }

  private HoldingPrice holding(Long id, long quantity, String current, String previous) {
    return new HoldingPrice(id, quantity, new BigDecimal(current), new BigDecimal(previous));
  }
}
