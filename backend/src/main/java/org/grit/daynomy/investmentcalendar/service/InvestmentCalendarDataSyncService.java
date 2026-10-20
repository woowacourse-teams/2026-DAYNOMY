package org.grit.daynomy.investmentcalendar.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class InvestmentCalendarDataSyncService {

  private final List<InvestmentCalendarProvider> providers;
  private final InvestmentEventSyncService eventSyncService;

  @Value("${investment-calendar.sync.history-years:5}")
  private int historyYears;

  @Value("${investment-calendar.sync.dart-lookback-days:30}")
  private int dartLookbackDays;

  public InvestmentEventSyncResult synchronize(LocalDate today) {
    int created = 0;
    int updated = 0;
    int unchanged = 0;
    for (InvestmentCalendarProvider provider : providers) {
      try {
        List<InvestmentEventEntry> entries = provider.fetch(today, historyYears, dartLookbackDays);
        InvestmentEventSyncResult result = eventSyncService.synchronize(entries);
        created += result.createdCount();
        updated += result.updatedCount();
        unchanged += result.unchangedCount();
        log.info(
            "Investment calendar provider synchronized: provider={}, fetchedCount={}, createdCount={}, updatedCount={}, unchangedCount={}",
            provider.getClass().getSimpleName(),
            entries.size(),
            result.createdCount(),
            result.updatedCount(),
            result.unchangedCount());
      } catch (RuntimeException exception) {
        log.warn(
            "Investment calendar provider synchronization failed: provider={}, errorType={}",
            provider.getClass().getSimpleName(),
            exception.getClass().getSimpleName());
      }
    }
    return new InvestmentEventSyncResult(created, updated, unchanged);
  }
}
