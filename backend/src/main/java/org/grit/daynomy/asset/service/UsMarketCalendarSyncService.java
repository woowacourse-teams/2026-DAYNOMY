package org.grit.daynomy.asset.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.us.calendar.sync.enabled", havingValue = "true")
public class UsMarketCalendarSyncService {
  private final EodhdClient client;
  private final MarketTradingDayRepository days;
  private final PlatformTransactionManager transactionManager;

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    if (days.findById(today).map(MarketTradingDay::getUsTrading).orElse(null) == null)
      synchronize();
  }

  @Scheduled(cron = "${stock.us.calendar.sync.cron:0 0 8 * * MON}", zone = "Asia/Seoul")
  public void synchronize() {
    int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    for (int target = year - 1; target <= year + 1; target++) {
      try {
        LocalDate first = LocalDate.of(target, 1, 1);
        LocalDate last = LocalDate.of(target, 12, 31);
        Set<LocalDate> holidays = client.getUsMarketHolidays(first, last);
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  saveYear(first, last, holidays);
                  return null;
                });
      } catch (RuntimeException exception) {
        log.warn(
            "US market calendar sync failed: year={}, errorType={}",
            target,
            exception.getClass().getSimpleName());
      }
    }
  }

  void saveYear(LocalDate first, LocalDate last, Set<LocalDate> holidays) {
    var existing =
        days.findCalendarForUpdate(first, last).stream()
            .collect(java.util.stream.Collectors.toMap(MarketTradingDay::getBaseDate, day -> day));
    var added = new ArrayList<MarketTradingDay>();
    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
      boolean trading =
          date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue() && !holidays.contains(date);
      MarketTradingDay day = existing.get(date);
      if (day == null) {
        day = new MarketTradingDay(date, false);
        day.reviseUsTrading(trading);
        added.add(day);
      } else {
        day.reviseUsTrading(trading);
      }
    }
    days.saveAll(added);
  }
}
