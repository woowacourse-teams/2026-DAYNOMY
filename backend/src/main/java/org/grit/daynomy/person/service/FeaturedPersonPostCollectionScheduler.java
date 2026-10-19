package org.grit.daynomy.person.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
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
      log.atWarn()
          .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_SKIPPED.code())
          .addKeyValue("reasonCode", "PREVIOUS_RUN_ACTIVE")
          .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_SKIPPED.message());
      return;
    }
    long startedAt = System.nanoTime();
    try {
      LocalDate to = LocalDate.now(COLLECTION_ZONE);
      LocalDate from = to.minusDays(lookbackDays - 1L);
      var people = personRepository.findAllByOrderByCreatedAtAscIdAsc();
      int successCount = 0;
      int failureCount = 0;
      log.atDebug()
          .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_STARTED.code())
          .addKeyValue("personCount", people.size())
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_STARTED.message());
      for (var person : people) {
        try {
          collectionService.collect(person.getId(), from, to);
          successCount++;
        } catch (BusinessException exception) {
          failureCount++;
          log.atWarn()
              .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_FAILED.code())
              .addKeyValue("personId", person.getId())
              .addKeyValue("errorCode", exception.errorCode().code())
              .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_FAILED.message());
        } catch (RuntimeException exception) {
          failureCount++;
          log.atError()
              .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_FAILED.code())
              .addKeyValue("personId", person.getId())
              .addKeyValue("exceptionType", exception.getClass().getSimpleName())
              .setCause(exception)
              .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_FAILED.message());
        }
      }
      log.atInfo()
          .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_COMPLETED.code())
          .addKeyValue("personCount", people.size())
          .addKeyValue("successCount", successCount)
          .addKeyValue("failureCount", failureCount)
          .addKeyValue("from", from)
          .addKeyValue("to", to)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_COMPLETED.message());
    } finally {
      running.set(false);
    }
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
