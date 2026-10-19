package org.grit.daynomy.portfolio.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.asset.service.StockPriceSyncResult;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.SanitizedLogException;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCurrentPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioPerformancePointResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceStatus;
import org.grit.daynomy.portfolio.dto.PortfolioPerformanceUnavailableReason;
import org.grit.daynomy.portfolio.dto.PortfolioPricingStatus;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PortfolioSnapshotService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingRepository holdingRepository;
  private final PortfolioDailySnapshotRepository snapshotRepository;
  private final StockDailyPriceRepository stockPriceRepository;
  private final PortfolioSnapshotTransactionService snapshotTransactionService;
  private final PortfolioCalculationService calculationService;

  public int createLatestSnapshots() {
    LocalDate today = LocalDate.now(SEOUL);
    NavigableSet<LocalDate> priceDates =
        new TreeSet<>(stockPriceRepository.findDistinctBaseDatesOrderByBaseDate());
    NavigableSet<LocalDate> candidateDates = new TreeSet<>(priceDates);
    candidateDates.addAll(snapshotRepository.findDistinctBaseDatesOrderByBaseDate());
    List<LocalDate> baseDates = candidateDates.headSet(today, false).stream().toList();
    if (baseDates.isEmpty()) {
      return 0;
    }

    List<Portfolio> portfolios = portfolioRepository.findAll();
    int snapshotCount = 0;
    int failureCount = 0;
    for (LocalDate baseDate : baseDates) {
      LocalDate previousBaseDate = priceDates.lower(baseDate);
      for (Portfolio portfolio : portfolios) {
        try {
          if (snapshotTransactionService.createSnapshot(
              portfolio.getId(), baseDate, previousBaseDate)) {
            snapshotCount++;
          }
        } catch (RuntimeException exception) {
          failureCount++;
          log.atError()
              .setCause(SanitizedLogException.from(exception))
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

  @EventListener(StockPriceSyncResult.class)
  public void refreshAfterPriceSync() {
    try {
      createLatestSnapshots();
    } catch (RuntimeException exception) {
      // 종가 저장은 이미 커밋됐다. 스냅샷 실패는 종가 수집 성공을 취소하지 않는다.
      log.atError()
          .setCause(SanitizedLogException.from(exception))
          .addKeyValue("event", LogEvent.PORTFOLIO_SNAPSHOT_FAILED.code())
          .log("종가 동기화 후 포트폴리오 스냅샷 갱신 실패");
    }
  }

  @Transactional(readOnly = true)
  public PortfolioPerformanceResponse performance(Long memberId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_PERIOD);
    }
    LocalDate today = LocalDate.now(SEOUL);
    Optional<Portfolio> portfolio = portfolioRepository.findByMemberId(memberId);
    LocalDate historicalTo = to.isBefore(today) ? to : today.minusDays(1);
    List<PortfolioDailySnapshot> snapshots =
        portfolio
            .filter(ignored -> !from.isAfter(historicalTo))
            .map(
                savedPortfolio ->
                    snapshotRepository.findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
                        savedPortfolio.getId(), from, historicalTo))
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
        snapshots.stream().map(PortfolioPerformancePointResponse::from).toList(),
        currentPoint(portfolio.orElse(null), from, to, today));
  }

  private PortfolioCurrentPerformanceResponse currentPoint(
      Portfolio portfolio, LocalDate from, LocalDate to, LocalDate today) {
    if (portfolio == null || today.isBefore(from) || today.isAfter(to)) {
      return null;
    }
    List<PortfolioHolding> holdings =
        holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId());
    if (holdings.isEmpty()) {
      return PortfolioCurrentPerformanceResponse.empty(today);
    }
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            holdings.stream()
                .map(
                    holding ->
                        new PortfolioHoldingRequest(
                            holding.getAsset().getId(),
                            holding.getQuantity(),
                            holding.getAveragePurchasePrice()))
                .toList());
    var calculation = calculationService.calculate(request);
    if (calculation.pricingStatus() != PortfolioPricingStatus.COMPLETE) {
      return null;
    }
    return PortfolioCurrentPerformanceResponse.from(today, calculation);
  }
}
