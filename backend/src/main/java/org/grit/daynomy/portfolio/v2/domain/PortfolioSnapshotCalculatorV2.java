package org.grit.daynomy.portfolio.v2.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator.Position;
import org.springframework.stereotype.Component;

@Component
public class PortfolioSnapshotCalculatorV2 {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  public Optional<Amounts> calculate(
      Collection<Position> holdings,
      Map<Long, BigDecimal> prices,
      Map<Long, BigDecimal> previousPrices,
      boolean hasPreviousBaseDate) {
    if (holdings.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal purchase = BigDecimal.ZERO;
    BigDecimal evaluation = BigDecimal.ZERO;
    BigDecimal previousEvaluation = BigDecimal.ZERO;
    for (Position holding : holdings) {
      BigDecimal price = prices.get(holding.asset().getId());
      if (price == null) {
        return Optional.empty();
      }
      BigDecimal quantity = BigDecimal.valueOf(holding.quantity());
      purchase = purchase.add(holding.averagePurchasePrice().multiply(quantity));
      evaluation = evaluation.add(price.multiply(quantity));
      if (hasPreviousBaseDate) {
        BigDecimal previous = previousPrices.get(holding.asset().getId());
        if (previous == null) {
          return Optional.empty();
        }
        previousEvaluation = previousEvaluation.add(previous.multiply(quantity));
      }
    }
    BigDecimal totalPurchase = money(purchase);
    BigDecimal totalEvaluation = money(evaluation);
    BigDecimal profit = money(totalEvaluation.subtract(totalPurchase));
    BigDecimal dailyProfit =
        hasPreviousBaseDate ? money(totalEvaluation.subtract(money(previousEvaluation))) : null;
    return Optional.of(
        new Amounts(
            totalPurchase,
            totalEvaluation,
            profit,
            percentage(profit, totalPurchase),
            dailyProfit,
            hasPreviousBaseDate ? percentage(dailyProfit, money(previousEvaluation)) : null));
  }

  private BigDecimal money(BigDecimal amount) {
    return withPrecision(amount, 19);
  }

  private BigDecimal percentage(BigDecimal amount, BigDecimal total) {
    if (total.signum() == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return withPrecision(amount.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP), 10);
  }

  private BigDecimal withPrecision(BigDecimal value, int precision) {
    BigDecimal result = value.setScale(2, RoundingMode.HALF_UP);
    if (result.precision() > precision) {
      throw new ArithmeticException("스냅샷 계산 결과가 저장 범위를 초과했습니다.");
    }
    return result;
  }

  public record Amounts(
      BigDecimal totalPurchase,
      BigDecimal totalEvaluation,
      BigDecimal totalProfitLoss,
      BigDecimal totalReturnRate,
      BigDecimal dailyProfitLoss,
      BigDecimal dailyReturnRate) {}
}
