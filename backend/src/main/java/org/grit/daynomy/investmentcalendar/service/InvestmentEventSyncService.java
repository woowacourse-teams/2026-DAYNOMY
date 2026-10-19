package org.grit.daynomy.investmentcalendar.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentEventSyncService {

  private final InvestmentEventRepository eventRepository;

  @Transactional
  public InvestmentEventSyncResult synchronize(List<InvestmentEventEntry> entries) {
    int createdCount = 0;
    int updatedCount = 0;
    int unchangedCount = 0;

    for (InvestmentEventEntry entry : entries) {
      InvestmentEvent event = eventRepository.findBySourceKey(entry.sourceKey()).orElse(null);
      if (event == null) {
        eventRepository.save(toEvent(entry));
        createdCount++;
        continue;
      }
      if (event.synchronize(
          entry.title(),
          entry.announcedAt(),
          entry.previousValue(),
          entry.actualValue(),
          entry.valueUnit(),
          entry.sourceName(),
          entry.sourceUrl(),
          entry.relatedAssetCode())) {
        updatedCount++;
      } else {
        unchangedCount++;
      }
    }
    return new InvestmentEventSyncResult(createdCount, updatedCount, unchangedCount);
  }

  private InvestmentEvent toEvent(InvestmentEventEntry entry) {
    return new InvestmentEvent(
        entry.type(),
        entry.title(),
        entry.announcedAt(),
        entry.previousValue(),
        entry.actualValue(),
        entry.valueUnit(),
        entry.sourceName(),
        entry.sourceUrl(),
        entry.sourceKey(),
        entry.relatedAssetCode());
  }
}
