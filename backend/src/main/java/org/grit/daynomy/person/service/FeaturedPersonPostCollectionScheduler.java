package org.grit.daynomy.person.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnProperty(name = "person.post-collection.enabled", havingValue = "true")
@Component
public class FeaturedPersonPostCollectionScheduler {

  private static final ZoneId COLLECTION_ZONE = ZoneId.of("Asia/Seoul");

  private final FeaturedPersonRepository personRepository;
  private final FeaturedPersonPostCollectionService collectionService;
  private final int lookbackDays;
  private final AtomicBoolean running = new AtomicBoolean();

  public FeaturedPersonPostCollectionScheduler(
      FeaturedPersonRepository personRepository,
      FeaturedPersonPostCollectionService collectionService,
      @Value("${person.post-collection.lookback-days:3}") int lookbackDays) {
    this.personRepository = personRepository;
    this.collectionService = collectionService;
    this.lookbackDays = Math.min(31, Math.max(1, lookbackDays));
  }

  @Scheduled(cron = "${person.post-collection.cron}", zone = "Asia/Seoul")
  public void collect() {
    if (!running.compareAndSet(false, true)) {
      log.warn("Featured person post collection skipped because a previous run is active");
      return;
    }
    try {
      LocalDate to = LocalDate.now(COLLECTION_ZONE);
      LocalDate from = to.minusDays(lookbackDays - 1L);
      for (var person : personRepository.findAllByOrderByCreatedAtAscIdAsc()) {
        try {
          collectionService.collect(person.getId(), from, to);
        } catch (BusinessException exception) {
          log.atWarn()
              .addKeyValue("personId", person.getId())
              .addKeyValue("errorCode", exception.errorCode().code())
              .log("Featured person post collection failed");
        } catch (RuntimeException exception) {
          log.atError()
              .addKeyValue("personId", person.getId())
              .addKeyValue("exceptionType", exception.getClass().getSimpleName())
              .log("Featured person post collection failed unexpectedly");
        }
      }
    } finally {
      running.set(false);
    }
  }
}
