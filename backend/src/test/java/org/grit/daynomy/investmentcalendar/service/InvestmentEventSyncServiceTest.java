package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentEventSyncServiceTest {

  @Mock InvestmentEventRepository eventRepository;

  @Test
  void createsAnEventWhenSourceKeyDoesNotExist() {
    InvestmentEventEntry entry = entry(new BigDecimal("2.50"));
    given(eventRepository.findBySourceKey(entry.sourceKey())).willReturn(Optional.empty());
    InvestmentEventSyncService service = new InvestmentEventSyncService(eventRepository);

    InvestmentEventSyncResult result = service.synchronize(List.of(entry));

    assertThat(result).isEqualTo(new InvestmentEventSyncResult(1, 0, 0));
    then(eventRepository).should().save(org.mockito.ArgumentMatchers.any(InvestmentEvent.class));
  }

  @Test
  void updatesAnEventWhenOfficialValueChanges() {
    InvestmentEventEntry entry = entry(new BigDecimal("2.75"));
    InvestmentEvent saved =
        new InvestmentEvent(
            entry.type(),
            entry.title(),
            entry.announcedAt(),
            entry.previousValue(),
            new BigDecimal("2.50"),
            entry.valueUnit(),
            entry.sourceName(),
            entry.sourceUrl(),
            entry.sourceKey(),
            entry.relatedAssetCode());
    given(eventRepository.findBySourceKey(entry.sourceKey())).willReturn(Optional.of(saved));
    InvestmentEventSyncService service = new InvestmentEventSyncService(eventRepository);

    InvestmentEventSyncResult result = service.synchronize(List.of(entry));

    assertThat(result).isEqualTo(new InvestmentEventSyncResult(0, 1, 0));
    assertThat(saved.getActualValue()).isEqualByComparingTo("2.75");
  }

  private InvestmentEventEntry entry(BigDecimal actualValue) {
    return new InvestmentEventEntry(
        InvestmentEventType.KOREA_BASE_RATE,
        "한국 기준금리 결정",
        Instant.parse("2026-10-29T01:00:00Z"),
        new BigDecimal("2.50"),
        actualValue,
        "%",
        "한국은행",
        "https://www.bok.or.kr",
        "BOK-2026-10",
        null);
  }
}
