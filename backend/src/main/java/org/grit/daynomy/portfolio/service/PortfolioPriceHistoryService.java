package org.grit.daynomy.portfolio.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient.HistoricalPrice;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioPriceHistoryService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingHistoryRepository historyRepository;
  private final PortfolioLedgerCalculator ledgerCalculator;
  private final AssetRepository assetRepository;
  private final StockDailyPriceRepository priceRepository;
  private final MarketTradingDayRepository tradingDayRepository;
  private final InvestmentCalendarHistoricalPriceClient domesticClient;
  private final EodhdClient usClient;
  private final KrxCalendarClient krxCalendarClient;
  private final PortfolioSnapshotTransactionService snapshotService;
  private final TaskScheduler taskScheduler;
  private final PlatformTransactionManager transactionManager;
  private final Map<Long, JobStatus> jobs = new ConcurrentHashMap<>();
  private final Map<Long, Range> refreshedRanges = new ConcurrentHashMap<>();
  private final Map<Long, Object> assetLocks = new ConcurrentHashMap<>();

  public enum Status {
    READY,
    PROCESSING,
    FAILED
  }

  public synchronized Status prepare(
      Long memberId, LocalDate requestedFrom, LocalDate requestedTo, boolean retry) {
    if (requestedFrom.isAfter(requestedTo)) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_PERIOD);
    }
    LocalDate today = LocalDate.now(SEOUL);
    LocalDate from =
        requestedFrom.isBefore(today.minusYears(5)) ? today.minusYears(5) : requestedFrom;
    LocalDate to = requestedTo.isBefore(today) ? requestedTo : today.minusDays(1);
    if (from.isAfter(to)) return Status.READY;
    var portfolio = portfolioRepository.findByMemberId(memberId);
    if (portfolio.isEmpty()) return Status.READY;
    Long portfolioId = portfolio.orElseThrow().getId();
    JobStatus job = jobs.get(portfolioId);
    if (job == JobStatus.PROCESSING) return Status.PROCESSING;
    if (job == JobStatus.FAILED && !retry) return Status.FAILED;

    List<Target> targets;
    try {
      targets = targets(portfolioId, from, to);
    } catch (BusinessException exception) {
      if (exception.errorCode() != ExternalErrorCode.KRX_CALENDAR_REQUEST_FAILED) throw exception;
      jobs.put(portfolioId, JobStatus.FAILED);
      return Status.FAILED;
    }
    boolean hasMissingCoverage =
        targets.stream()
            .anyMatch(
                target ->
                    !assetRepository
                        .findById(target.assetId())
                        .orElseThrow()
                        .hasPriceHistory(target.from(), target.to()));
    Range refreshed = refreshedRanges.get(portfolioId);
    if (!hasMissingCoverage
        && refreshed != null
        && !refreshed.from().isAfter(from)
        && !refreshed.to().isBefore(to)) return Status.READY;

    jobs.put(portfolioId, JobStatus.PROCESSING);
    try {
      taskScheduler.schedule(() -> collect(portfolioId, from, to, targets), Instant.now());
    } catch (RuntimeException exception) {
      jobs.put(portfolioId, JobStatus.FAILED);
      throw exception;
    }
    return Status.PROCESSING;
  }

  private List<Target> targets(Long portfolioId, LocalDate from, LocalDate to) {
    List<PortfolioHoldingHistory> histories =
        historyRepository.findOrderedWithAssetsByPortfolioId(portfolioId).stream()
            .filter(history -> !history.getEffectiveDate().isAfter(to))
            .toList();
    Map<Long, Target> targets = new LinkedHashMap<>();
    List<PortfolioHoldingHistory> processed = new ArrayList<>();
    int index = 0;
    while (index < histories.size() && histories.get(index).getEffectiveDate().isBefore(from)) {
      processed.add(histories.get(index++));
    }
    LocalDate intervalStart = from;
    while (index < histories.size()) {
      LocalDate eventDate = histories.get(index).getEffectiveDate();
      if (intervalStart.isBefore(eventDate)) {
        addHeldTargets(targets, processed, intervalStart, eventDate.minusDays(1));
      }
      while (index < histories.size()
          && histories.get(index).getEffectiveDate().equals(eventDate)) {
        processed.add(histories.get(index++));
      }
      intervalStart = eventDate;
    }
    addHeldTargets(targets, processed, intervalStart, to);
    return List.copyOf(targets.values());
  }

  private void addHeldTargets(
      Map<Long, Target> targets,
      List<PortfolioHoldingHistory> histories,
      LocalDate from,
      LocalDate to) {
    if (histories.isEmpty() || from.isAfter(to)) return;
    ledgerCalculator
        .replay(histories)
        .positions()
        .values()
        .forEach(position -> addTarget(targets, position.asset(), from, to));
  }

  private void addTarget(Map<Long, Target> targets, Asset asset, LocalDate from, LocalDate to) {
    if (asset.getCategory() != AssetCategory.STOCK && asset.getCategory() != AssetCategory.ETF)
      return;
    LocalDate availableTo =
        asset.getMarket() != null && asset.getMarket().isUs()
            ? LocalDate.now(NEW_YORK).minusDays(1)
            : availableDomesticThrough(to, ZonedDateTime.now(SEOUL));
    LocalDate end = to.isBefore(availableTo) ? to : availableTo;
    if (from.isAfter(end)) return;
    targets.merge(
        asset.getId(),
        new Target(asset.getId(), from, end),
        (old, next) ->
            new Target(
                asset.getId(),
                old.from().isBefore(next.from()) ? old.from() : next.from(),
                old.to().isAfter(next.to()) ? old.to() : next.to()));
  }

  LocalDate availableDomesticThrough(LocalDate requestedTo, ZonedDateTime now) {
    Map<Integer, Set<LocalDate>> holidays = new HashMap<>();
    LocalDate through = requestedTo;
    while (true) {
      LocalDate next = through.plusDays(1);
      while (!isDomesticTradingDay(next, holidays)) {
        next = next.plusDays(1);
      }
      if (!now.toInstant().isBefore(next.atTime(14, 0).atZone(SEOUL).toInstant())) {
        return through;
      }
      through = through.minusDays(1);
    }
  }

  private boolean isDomesticTradingDay(LocalDate date, Map<Integer, Set<LocalDate>> holidays) {
    if (date.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue()) return false;
    return tradingDayRepository
        .findById(date)
        .map(MarketTradingDay::isTrading)
        .orElseGet(
            () ->
                !holidays
                    .computeIfAbsent(date.getYear(), krxCalendarClient::holidays)
                    .contains(date));
  }

  private void collect(Long portfolioId, LocalDate from, LocalDate to, List<Target> targets) {
    boolean failed = false;
    for (Target target : targets) {
      try {
        collect(target);
      } catch (RuntimeException exception) {
        failed = true;
        log.warn(
            "Portfolio price collection failed: assetId={}, errorType={}",
            target.assetId(),
            exception.getClass().getSimpleName());
      }
    }
    try {
      List<LocalDate> dates = priceRepository.findBaseDatesByPortfolioId(portfolioId);
      LocalDate previous = null;
      for (LocalDate date : dates) {
        if (!date.isBefore(from) && !date.isAfter(to)) {
          snapshotService.createSnapshot(portfolioId, date, previous);
        }
        previous = date;
      }
    } catch (RuntimeException exception) {
      failed = true;
      log.warn(
          "Portfolio snapshot refresh failed: portfolioId={}, errorType={}",
          portfolioId,
          exception.getClass().getSimpleName());
    }
    if (failed) {
      refreshedRanges.remove(portfolioId);
    } else {
      refreshedRanges.merge(
          portfolioId,
          new Range(from, to),
          (previous, next) -> {
            if (previous.to().plusDays(1).isBefore(next.from())
                || next.to().plusDays(1).isBefore(previous.from())) return next;
            return new Range(
                previous.from().isBefore(next.from()) ? previous.from() : next.from(),
                previous.to().isAfter(next.to()) ? previous.to() : next.to());
          });
    }
    jobs.put(portfolioId, failed ? JobStatus.FAILED : JobStatus.READY);
  }

  private void collect(Target target) {
    synchronized (assetLocks.computeIfAbsent(target.assetId(), ignored -> new Object())) {
      Asset asset = assetRepository.findById(target.assetId()).orElseThrow();
      if (asset.hasPriceHistory(target.from(), target.to())) return;
      LocalDate from = target.from();
      LocalDate to = target.to();
      if (asset.getPortfolioPriceHistoryFrom() != null
          && asset.getPortfolioPriceHistoryThrough() != null) {
        from =
            from.isBefore(asset.getPortfolioPriceHistoryFrom())
                ? from
                : asset.getPortfolioPriceHistoryThrough().plusDays(1);
        to =
            to.isAfter(asset.getPortfolioPriceHistoryThrough())
                ? to
                : asset.getPortfolioPriceHistoryFrom().minusDays(1);
        if (from.isAfter(to)) {
          from = target.from();
          to = target.to();
        }
      }
      LocalDate fetchFrom = from;
      LocalDate fetchTo = to;
      List<LocalDate> tradingDates = tradingDates(asset, fetchFrom, fetchTo);
      Set<LocalDate> storedDates = new HashSet<>();
      priceRepository
          .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
              List.of(target.assetId()), fetchFrom, fetchTo)
          .forEach(price -> storedDates.add(price.getBaseDate()));
      List<HistoricalPrice> fetched =
          tradingDates.stream().allMatch(storedDates::contains)
              ? List.of()
              : fetch(asset, fetchFrom, fetchTo);
      Boolean complete =
          new TransactionTemplate(transactionManager)
              .execute(
                  status -> {
                    Asset storedAsset = assetRepository.findById(target.assetId()).orElseThrow();
                    Set<LocalDate> present = new HashSet<>();
                    priceRepository
                        .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                            List.of(target.assetId()), fetchFrom, fetchTo)
                        .forEach(price -> present.add(price.getBaseDate()));
                    List<StockDailyPrice> additions = new ArrayList<>();
                    for (HistoricalPrice price : fetched) {
                      if (price.baseDate() == null
                          || price.baseDate().isBefore(fetchFrom)
                          || price.baseDate().isAfter(fetchTo)
                          || price.closePrice() == null
                          || price.closePrice().signum() <= 0) continue;
                      if (present.add(price.baseDate())) {
                        additions.add(
                            new StockDailyPrice(storedAsset, price.baseDate(), price.closePrice()));
                      }
                    }
                    priceRepository.saveAll(additions);
                    List<LocalDate> missing =
                        tradingDates.stream().filter(date -> !present.contains(date)).toList();
                    LocalDate coveredFrom = fetchFrom;
                    LocalDate coveredTo = fetchTo;
                    if (!missing.isEmpty()) {
                      if (storedAsset.getPortfolioPriceHistoryFrom() != null
                          && fetchTo.isBefore(storedAsset.getPortfolioPriceHistoryFrom())) {
                        coveredFrom = missing.getLast().plusDays(1);
                      } else if (storedAsset.getPortfolioPriceHistoryThrough() == null
                          || fetchFrom.isAfter(storedAsset.getPortfolioPriceHistoryThrough())) {
                        coveredTo = missing.getFirst().minusDays(1);
                      } else {
                        coveredFrom = fetchTo.plusDays(1);
                      }
                    }
                    if (!coveredFrom.isAfter(coveredTo)) {
                      storedAsset.recordPriceHistoryCoverage(coveredFrom, coveredTo);
                    }
                    return missing.isEmpty();
                  });
      if (!Boolean.TRUE.equals(complete)) {
        throw new IllegalStateException("Missing historical prices for trading dates");
      }
    }
  }

  private List<LocalDate> tradingDates(Asset asset, LocalDate from, LocalDate to) {
    List<LocalDate> weekdays =
        from.datesUntil(to.plusDays(1))
            .filter(date -> date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue())
            .toList();
    if (weekdays.isEmpty()) return List.of();
    if (asset.getMarket() != null && asset.getMarket().isUs()) {
      Set<LocalDate> holidays = usClient.getUsMarketHolidays(from, to);
      return weekdays.stream().filter(date -> !holidays.contains(date)).toList();
    }
    Map<LocalDate, Boolean> calendar = new HashMap<>();
    for (MarketTradingDay day :
        tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(from, to)) {
      calendar.put(day.getBaseDate(), day.isTrading());
    }
    Map<Integer, Set<LocalDate>> missingYears = new HashMap<>();
    List<LocalDate> tradingDates = new ArrayList<>();
    for (LocalDate date : weekdays) {
      Boolean trading = calendar.get(date);
      if (trading == null) {
        trading =
            !missingYears
                .computeIfAbsent(date.getYear(), krxCalendarClient::holidays)
                .contains(date);
      }
      if (trading) tradingDates.add(date);
    }
    return tradingDates;
  }

  private List<HistoricalPrice> fetch(Asset asset, LocalDate from, LocalDate to) {
    if (asset.getMarket() != null && asset.getMarket().isUs()) {
      return usClient.getHistoricalPrices(asset.getAssetCode(), from, to).stream()
          .filter(price -> price.date() != null && price.close() != null)
          .map(price -> new HistoricalPrice(price.date(), price.close()))
          .toList();
    }
    return domesticClient.fetch(asset.getCategory(), asset.getAssetCode(), from, to);
  }

  private record Target(Long assetId, LocalDate from, LocalDate to) {}

  private record Range(LocalDate from, LocalDate to) {}

  private enum JobStatus {
    READY,
    PROCESSING,
    FAILED
  }
}
