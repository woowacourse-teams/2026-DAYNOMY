package org.grit.daynomy.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "content.collection.enabled", havingValue = "true")
@Component
public class ContentCollectionScheduler {

  private final ContentCollectionBatchService batchService;

  @Scheduled(cron = "${content.collection.cron}", zone = "Asia/Seoul")
  public void collect() {
    try {
      batchService.collectAllEnabled();
    } catch (BusinessException exception) {
      if (exception.errorCode() != ContentErrorCode.CONTENT_COLLECTION_ALREADY_RUNNING) {
        throw exception;
      }
      log.atWarn()
          .addKeyValue("event", LogEvent.CONTENT_COLLECTION_SKIPPED.code())
          .addKeyValue("reasonCode", "PREVIOUS_RUN_ACTIVE")
          .log(LogEvent.CONTENT_COLLECTION_SKIPPED.message());
    }
  }
}
