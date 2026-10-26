package org.grit.daynomy.emailsubscription.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record ImportantStockSelection(
    LocalDate baseDate, LocalDate previousBaseDate, boolean completePrices, List<Stock> stocks) {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal PRICE_CHANGE_THRESHOLD = BigDecimal.valueOf(5);
  private static final int MAX_STOCKS = 3;

  public ImportantStockSelection {
    stocks = List.copyOf(stocks);
  }

  public static ImportantStockSelection select(
      LocalDate baseDate, LocalDate previousBaseDate, List<HoldingPrice> holdings) {
    return select(baseDate, previousBaseDate, holdings, true);
  }

  public static ImportantStockSelection select(
      LocalDate baseDate,
      LocalDate previousBaseDate,
      List<HoldingPrice> holdings,
      boolean comparableCurrency) {
    boolean complete =
        comparableCurrency && holdings.stream().allMatch(HoldingPrice::hasBothPrices);
    BigDecimal total =
        complete
            ? holdings.stream()
                .map(HoldingPrice::previousValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;

    List<Stock> selected =
        holdings.stream()
            .filter(HoldingPrice::hasBothPrices)
            .filter(holding -> holding.isImportant(total))
            .sorted(priority(total))
            .limit(MAX_STOCKS)
            .map(holding -> holding.toStock(total))
            .toList();
    return new ImportantStockSelection(baseDate, previousBaseDate, complete, selected);
  }

  private static Comparator<HoldingPrice> priority(BigDecimal total) {
    if (total.signum() == 0) {
      return Comparator.comparing(HoldingPrice::assetId);
    }
    // 모든 종목의 분모가 같으므로 나눗셈 반올림 없이 기여도와 비중을 비교한다.
    return Comparator.comparing((HoldingPrice holding) -> holding.valueChange().abs())
        .reversed()
        .thenComparing(HoldingPrice::previousValue, Comparator.reverseOrder())
        .thenComparing(HoldingPrice::assetId);
  }

  private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
    return numerator.multiply(HUNDRED).divide(denominator, 10, RoundingMode.HALF_UP);
  }

  public record HoldingPrice(
      Long assetId, long quantity, BigDecimal closePrice, BigDecimal previousClosePrice) {

    private boolean hasBothPrices() {
      return closePrice != null && previousClosePrice != null;
    }

    private BigDecimal previousValue() {
      return previousClosePrice.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal priceChange() {
      return closePrice.subtract(previousClosePrice);
    }

    private BigDecimal valueChange() {
      return priceChange().multiply(BigDecimal.valueOf(quantity));
    }

    private boolean isImportant(BigDecimal total) {
      boolean priceChanged =
          priceChange()
                  .abs()
                  .multiply(HUNDRED)
                  .compareTo(previousClosePrice.multiply(PRICE_CHANGE_THRESHOLD))
              >= 0;
      boolean contributes =
          total.signum() > 0 && valueChange().abs().multiply(HUNDRED).compareTo(total) >= 0;
      return priceChanged || contributes;
    }

    private Stock toStock(BigDecimal total) {
      return new Stock(
          assetId,
          percentage(priceChange(), previousClosePrice),
          total.signum() > 0 ? percentage(previousValue(), total) : null,
          total.signum() > 0 ? percentage(valueChange(), total) : null);
    }
  }

  /** 등락률·비중은 %, 자산 기여도는 %p. 가격이 불완전하면 비중·기여도는 알 수 없다. */
  public record Stock(
      Long assetId, BigDecimal dailyReturnRate, BigDecimal weight, BigDecimal contribution) {}
}
