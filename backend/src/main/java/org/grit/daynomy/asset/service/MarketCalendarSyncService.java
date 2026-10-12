package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@RequiredArgsConstructor
@Service
public class MarketCalendarSyncService {
  private final KrxCalendarClient client;
  private final MarketTradingDayRepository repository;
  private final PlatformTransactionManager transactionManager;
  private final ApplicationEventPublisher events;

  public synchronized void synchronize() {
    synchronize(LocalDate.now(ZoneId.of("Asia/Seoul")));
  }

  void synchronize(LocalDate today) {
    Set<Integer> publishedYears;
    try {
      publishedYears = client.publishedYears();
    } catch (RuntimeException exception) {
      logFailure(today.getYear(), exception);
      return;
    }
    int changed = 0;
    for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
      if (!publishedYears.contains(year)) continue;
      try {
        Set<LocalDate> holidays = client.holidays(year);
        int targetYear = year;
        Integer count =
            new TransactionTemplate(transactionManager)
                .execute(status -> synchronizeYear(targetYear, holidays));
        changed += count == null ? 0 : count;
      } catch (RuntimeException exception) {
        logFailure(year, exception);
      }
    }
    log.atInfo()
        .addKeyValue("event", LogEvent.MARKET_CALENDAR_SYNC_COMPLETED.code())
        .addKeyValue("changedDays", changed)
        .log(LogEvent.MARKET_CALENDAR_SYNC_COMPLETED.message());
    if (changed > 0) events.publishEvent(new MarketCalendarSyncResult(changed));
  }

  private int synchronizeYear(int year, Set<LocalDate> holidays) {
    LocalDate first = LocalDate.of(year, 1, 1);
    LocalDate last = LocalDate.of(year, 12, 31);
    Map<LocalDate, MarketTradingDay> existing =
        repository.findCalendarForUpdate(first, last).stream()
            .collect(Collectors.toMap(MarketTradingDay::getBaseDate, Function.identity()));
    var added = new ArrayList<MarketTradingDay>();
    int changed = 0;
    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
      boolean trading = date.getDayOfWeek().getValue() <= 5 && !holidays.contains(date);
      MarketTradingDay day = existing.get(date);
      if (day == null) {
        added.add(new MarketTradingDay(date, trading));
        changed++;
      } else if (day.reviseTrading(trading)) changed++;
    }
    if (!added.isEmpty()) repository.saveAll(added);
    return changed;
  }

  private void logFailure(int year, RuntimeException exception) {
    log.atError()
        .addKeyValue("event", LogEvent.MARKET_CALENDAR_SYNC_FAILED.code())
        .addKeyValue("year", year)
        .addKeyValue("exception", exception.getClass().getSimpleName())
        .log(LogEvent.MARKET_CALENDAR_SYNC_FAILED.message());
  }
}
