package org.grit.daynomy.content.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.dto.ContentCollectionBatchResultResponse;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ContentCollectionBatchService {

  private final ContentCollectionSourceRepository sourceRepository;
  private final ContentCollectionService collectionService;
  // The process guard rejects duplicate whole-batch requests; collect() locks each source in DB.
  private final AtomicBoolean running = new AtomicBoolean();

  public ContentCollectionBatchResultResponse collectAllEnabled() {
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(ContentErrorCode.CONTENT_COLLECTION_ALREADY_RUNNING);
    }
    long startedAt = System.nanoTime();
    try {
      var sources = sourceRepository.findAllByEnabledTrueOrderByIdAsc();
      int successCount = 0;
      int failureCount = 0;
      int fetchedCount = 0;
      int savedCount = 0;
      int duplicateCount = 0;
      int linkedCount = 0;
      log.atInfo()
          .addKeyValue("event", LogEvent.CONTENT_COLLECTION_STARTED.code())
          .addKeyValue("sourceCount", sources.size())
          .log(LogEvent.CONTENT_COLLECTION_STARTED.message());
      for (var source : sources) {
        try {
          ContentCollectionResultResponse result = collectionService.collect(source.getId());
          successCount++;
          fetchedCount += result.fetchedCount();
          savedCount += result.savedCount();
          duplicateCount += result.duplicateCount();
          linkedCount += result.linkedCount();
        } catch (BusinessException exception) {
          failureCount++;
          log.atWarn()
              .addKeyValue("event", LogEvent.CONTENT_COLLECTION_FAILED.code())
              .addKeyValue("sourceId", source.getId())
              .addKeyValue("errorCode", exception.errorCode().code())
              .log(LogEvent.CONTENT_COLLECTION_FAILED.message());
        } catch (RuntimeException exception) {
          failureCount++;
          log.atError()
              .addKeyValue("event", LogEvent.CONTENT_COLLECTION_FAILED.code())
              .addKeyValue("sourceId", source.getId())
              .addKeyValue("exceptionType", exception.getClass().getSimpleName())
              .setCause(exception)
              .log(LogEvent.CONTENT_COLLECTION_FAILED.message());
        }
      }
      ContentCollectionBatchResultResponse result =
          new ContentCollectionBatchResultResponse(
              sources.size(),
              successCount,
              failureCount,
              fetchedCount,
              savedCount,
              duplicateCount,
              linkedCount);
      log.atInfo()
          .addKeyValue("event", LogEvent.CONTENT_COLLECTION_COMPLETED.code())
          .addKeyValue("sourceCount", result.sourceCount())
          .addKeyValue("successCount", result.successCount())
          .addKeyValue("failureCount", result.failureCount())
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
          .log(LogEvent.CONTENT_COLLECTION_COMPLETED.message());
      return result;
    } finally {
      running.set(false);
    }
  }
}
