package org.grit.daynomy.asset.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher events;

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate first = LocalDate.of(today.getYear() - 1, 1, 1);
    LocalDate last = LocalDate.of(today.getYear() + 1, 12, 31);
    var calendar = days.findAllByBaseDateBetweenOrderByBaseDate(first, last);
    if (calendar.size() != ChronoUnit.DAYS.between(first, last) + 1
        || calendar.stream().anyMatch(day -> day.getUsTrading() == null)) synchronize();
  }

  @Scheduled(cron = "${stock.us.calendar.sync.cron:0 0 8,18 * * *}", zone = "Asia/Seoul")
  public synchronized void synchronize() {
    int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    int changed = 0;
    for (int target = year - 1; target <= year + 1; target++) {
      try {
        LocalDate first = LocalDate.of(target, 1, 1);
        LocalDate last = LocalDate.of(target, 12, 31);
        Set<LocalDate> holidays = client.getUsMarketHolidays(first, last);
        if (holidays.isEmpty()) {
          throw new IllegalStateException("US market holiday calendar is empty");
        }
        Integer count =
            new TransactionTemplate(transactionManager)
                .execute(status -> saveYear(first, last, holidays));
        changed += count == null ? 0 : count;
      } catch (RuntimeException exception) {
        log.warn(
            "US market calendar sync failed: year={}, errorType={}",
            target,
            exception.getClass().getSimpleName());
      }
    }
    if (changed > 0) events.publishEvent(new MarketCalendarSyncResult(changed));
  }

  int saveYear(LocalDate first, LocalDate last, Set<LocalDate> holidays) {
    var existing =
        days.findCalendarForUpdate(first, last).stream()
            .collect(java.util.stream.Collectors.toMap(MarketTradingDay::getBaseDate, day -> day));
    var added = new ArrayList<MarketTradingDay>();
    int changed = 0;
    for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
      boolean trading =
          date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue() && !holidays.contains(date);
      MarketTradingDay day = existing.get(date);
      if (day == null) {
        day = new MarketTradingDay(date, false);
        day.reviseUsTrading(trading);
        added.add(day);
        changed++;
      } else if (!Objects.equals(day.getUsTrading(), trading)) {
        day.reviseUsTrading(trading);
        changed++;
      }
    }
    if (!added.isEmpty()) days.saveAll(added);
    return changed;
  }
}
