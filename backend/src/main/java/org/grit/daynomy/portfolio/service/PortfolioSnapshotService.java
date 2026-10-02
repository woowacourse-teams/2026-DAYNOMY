package org.grit.daynomy.portfolio.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioPerformancePointResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PortfolioSnapshotService {

  private final PortfolioRepository portfolioRepository;
  private final PortfolioDailySnapshotRepository snapshotRepository;
  private final StockDailyPriceRepository stockPriceRepository;
  private final PortfolioSnapshotTransactionService snapshotTransactionService;

  public int createLatestSnapshots() {
    List<LocalDate> baseDates = stockPriceRepository.findDistinctBaseDatesOrderByBaseDate();
    if (baseDates.isEmpty()) {
      return 0;
    }

    List<Portfolio> portfolios = portfolioRepository.findAll();
    int snapshotCount = 0;
    int failureCount = 0;
    for (int dateIndex = 0; dateIndex < baseDates.size(); dateIndex++) {
      LocalDate baseDate = baseDates.get(dateIndex);
      LocalDate previousBaseDate = dateIndex == 0 ? null : baseDates.get(dateIndex - 1);
      for (Portfolio portfolio : portfolios) {
        try {
          if (snapshotTransactionService.createSnapshot(
              portfolio.getId(), baseDate, previousBaseDate)) {
            snapshotCount++;
          }
        } catch (RuntimeException exception) {
          failureCount++;
          log.atError()
              .setCause(exception)
              .addKeyValue("event", LogEvent.PORTFOLIO_SNAPSHOT_FAILED.code())
              .addKeyValue("portfolioId", portfolio.getId())
              .addKeyValue("baseDate", baseDate)
              .log("포트폴리오 스냅샷 생성 실패");
        }
      }
    }

    log.atInfo()
        .addKeyValue("event", LogEvent.PORTFOLIO_SNAPSHOT_COMPLETED.code())
        .addKeyValue("baseDate", baseDates.getLast())
        .addKeyValue("snapshotCount", snapshotCount)
        .addKeyValue("failureCount", failureCount)
        .log(LogEvent.PORTFOLIO_SNAPSHOT_COMPLETED.message());
    return snapshotCount;
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
}
