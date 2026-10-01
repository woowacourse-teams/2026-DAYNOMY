package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioPerformancePointResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PortfolioSnapshotService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingRepository holdingRepository;
  private final PortfolioDailySnapshotRepository snapshotRepository;
  private final StockDailyPriceRepository stockPriceRepository;

  @Transactional
  public int createLatestSnapshots() {
    LocalDate baseDate =
        stockPriceRepository
            .findFirstByOrderByBaseDateDesc()
            .map(StockDailyPrice::getBaseDate)
            .orElse(null);
    if (baseDate == null) {
      return 0;
    }
    int createdCount = 0;
    for (Portfolio portfolio : portfolioRepository.findAll()) {
      if (createSnapshot(portfolio, baseDate)) {
        createdCount++;
      }
    }
    log.atInfo()
        .addKeyValue("event", LogEvent.PORTFOLIO_SNAPSHOT_COMPLETED.code())
        .addKeyValue("baseDate", baseDate)
        .addKeyValue("portfolioCount", createdCount)
        .log(LogEvent.PORTFOLIO_SNAPSHOT_COMPLETED.message());
    return createdCount;
  }

  @Transactional(readOnly = true)
  public PortfolioPerformanceResponse performance(Long memberId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_PERIOD);
    }
    List<PortfolioDailySnapshot> snapshots =
        portfolioRepository
            .findByMemberId(memberId)
            .map(
                portfolio ->
                    snapshotRepository.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
                        portfolio.getId(), from, to))
            .orElseGet(List::of);
    PortfolioPerformanceStatus status =
        snapshots.size() >= 2
            ? PortfolioPerformanceStatus.READY
            : PortfolioPerformanceStatus.INSUFFICIENT_DATA;
    LocalDate baseDate = snapshots.isEmpty() ? null : snapshots.getLast().getBaseDate();
    LocalDate previousBaseDate =
        snapshots.size() < 2 ? null : snapshots.get(snapshots.size() - 2).getBaseDate();
    return new PortfolioPerformanceResponse(
        status,
        status == PortfolioPerformanceStatus.READY
            ? null
            : PortfolioPerformanceUnavailableReason.SNAPSHOT_DATA_INSUFFICIENT,
        baseDate,
        previousBaseDate,
        snapshots.stream().map(PortfolioPerformancePointResponse::from).toList());
  }

  private boolean createSnapshot(Portfolio portfolio, LocalDate baseDate) {
    List<PortfolioHolding> holdings =
        holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId());
    if (holdings.isEmpty()) {
      return false;
    }
    BigDecimal purchase = BigDecimal.ZERO;
    BigDecimal evaluation = BigDecimal.ZERO;
    for (PortfolioHolding holding : holdings) {
      StockDailyPrice price =
          stockPriceRepository
              .findByAssetIdAndBaseDate(holding.getAsset().getId(), baseDate)
              .orElse(null);
      if (price == null) {
        return false;
      }
      BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
      purchase = purchase.add(holding.getAveragePurchasePrice().multiply(quantity));
      evaluation = evaluation.add(price.getClosePrice().multiply(quantity));
    }
    BigDecimal totalPurchase = money(purchase);
    BigDecimal totalEvaluation = money(evaluation);
    BigDecimal profit = money(totalEvaluation.subtract(totalPurchase));
    BigDecimal returnRate = percentage(profit, totalPurchase);
    PortfolioDailySnapshot snapshot =
        snapshotRepository
            .findByPortfolioIdAndBaseDate(portfolio.getId(), baseDate)
            .orElseGet(
                () ->
                    new PortfolioDailySnapshot(
                        portfolio, baseDate, totalPurchase, totalEvaluation, profit, returnRate));
    snapshot.update(totalPurchase, totalEvaluation, profit, returnRate);
    snapshotRepository.save(snapshot);
    return true;
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal percentage(BigDecimal amount, BigDecimal total) {
    if (total.signum() == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return amount.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
  }
}
