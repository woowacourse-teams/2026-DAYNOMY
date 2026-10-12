package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioSnapshotTransactionService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int MONEY_PRECISION = 19;
  private static final int RATE_PRECISION = 10;

  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingHistoryRepository historyRepository;
  private final PortfolioDailySnapshotRepository snapshotRepository;
  private final StockDailyPriceRepository stockPriceRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean createSnapshot(Long portfolioId, LocalDate baseDate, LocalDate previousBaseDate) {
    Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
    if (portfolio == null) {
      return false;
    }

    PortfolioDailySnapshot existing =
        snapshotRepository.findByPortfolioIdAndBaseDate(portfolioId, baseDate).orElse(null);
    if (existing != null && baseDate.isBefore(LocalDate.now(SEOUL))) {
      return false;
    }

    List<PortfolioHoldingHistory> holdings = reconstructHoldings(portfolioId, baseDate);
    if (holdings.isEmpty()) {
      return false;
    }

    SnapshotAmounts amounts = calculateAmounts(holdings, baseDate, previousBaseDate);
    if (amounts == null) {
      return false;
    }

    PortfolioDailySnapshot snapshot =
        existing != null ? existing : new PortfolioDailySnapshot(portfolio, baseDate);
    snapshot.update(
        amounts.totalPurchase(),
        amounts.totalEvaluation(),
        amounts.totalProfitLoss(),
        amounts.totalReturnRate(),
        amounts.dailyProfitLoss(),
        amounts.dailyReturnRate());
    snapshotRepository.saveAndFlush(snapshot);
    return true;
  }

  private List<PortfolioHoldingHistory> reconstructHoldings(Long portfolioId, LocalDate baseDate) {
    Map<Long, PortfolioHoldingHistory> holdings = new LinkedHashMap<>();
    List<PortfolioHoldingHistory> histories =
        historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            portfolioId, baseDate.plusDays(1).atStartOfDay(SEOUL).toInstant());
    for (PortfolioHoldingHistory history : histories) {
      Long assetId = history.getAsset().getId();
      if (history.getChangeType() == PortfolioHoldingChangeType.REMOVED) {
        holdings.remove(assetId);
      } else {
        holdings.put(assetId, history);
      }
    }
    return List.copyOf(holdings.values());
  }

  private SnapshotAmounts calculateAmounts(
      List<PortfolioHoldingHistory> holdings, LocalDate baseDate, LocalDate previousBaseDate) {
    BigDecimal purchase = BigDecimal.ZERO;
    BigDecimal evaluation = BigDecimal.ZERO;
    BigDecimal previousEvaluation = BigDecimal.ZERO;

    for (PortfolioHoldingHistory holding : holdings) {
      StockDailyPrice price = findPrice(holding.getAsset().getId(), baseDate);
      if (price == null) {
        return null;
      }
      BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
      purchase = purchase.add(holding.getAveragePurchasePrice().multiply(quantity));
      evaluation = evaluation.add(price.getClosePrice().multiply(quantity));

      if (previousBaseDate != null) {
        StockDailyPrice previousPrice = findPrice(holding.getAsset().getId(), previousBaseDate);
        if (previousPrice == null) {
          return null;
        }
        previousEvaluation =
            previousEvaluation.add(previousPrice.getClosePrice().multiply(quantity));
      }
    }

    BigDecimal totalPurchase = money(purchase);
    BigDecimal totalEvaluation = money(evaluation);
    BigDecimal totalProfitLoss = money(totalEvaluation.subtract(totalPurchase));
    BigDecimal totalReturnRate = percentage(totalProfitLoss, totalPurchase);
    BigDecimal dailyProfitLoss = null;
    BigDecimal dailyReturnRate = null;
    if (previousBaseDate != null) {
      BigDecimal previousTotalEvaluation = money(previousEvaluation);
      dailyProfitLoss = money(totalEvaluation.subtract(previousTotalEvaluation));
      dailyReturnRate = percentage(dailyProfitLoss, previousTotalEvaluation);
    }
    return new SnapshotAmounts(
        totalPurchase,
        totalEvaluation,
        totalProfitLoss,
        totalReturnRate,
        dailyProfitLoss,
        dailyReturnRate);
  }

  private StockDailyPrice findPrice(Long assetId, LocalDate baseDate) {
    return stockPriceRepository.findByAssetIdAndBaseDate(assetId, baseDate).orElse(null);
  }

  private BigDecimal money(BigDecimal value) {
    return withPrecision(value, MONEY_PRECISION);
  }

  private BigDecimal percentage(BigDecimal amount, BigDecimal total) {
    if (total.signum() == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return withPrecision(
        amount.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP), RATE_PRECISION);
  }

  private BigDecimal withPrecision(BigDecimal value, int precision) {
    BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
    if (scaled.precision() > precision) {
      throw new ArithmeticException("스냅샷 계산 결과가 저장 범위를 초과했습니다.");
    }
    return scaled;
  }

  private record SnapshotAmounts(
      BigDecimal totalPurchase,
      BigDecimal totalEvaluation,
      BigDecimal totalProfitLoss,
      BigDecimal totalReturnRate,
      BigDecimal dailyProfitLoss,
      BigDecimal dailyReturnRate) {}
}
