package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ContentCollectionSchedulerTest {

  @Mock private ContentCollectionBatchService batchService;

  private final Logger logger = (Logger) LoggerFactory.getLogger(ContentCollectionScheduler.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUpLogging() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogging() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void delegatesToBatchCollection() {
    new ContentCollectionScheduler(batchService).collect();

    then(batchService).should().collectAllEnabled();
  }

  @Test
  void skipsWhenBatchCollectionIsAlreadyRunning() {
    given(batchService.collectAllEnabled())
        .willThrow(new BusinessException(ContentErrorCode.CONTENT_COLLECTION_ALREADY_RUNNING));

    new ContentCollectionScheduler(batchService).collect();

    then(batchService).should().collectAllEnabled();
    assertThat(keyValues(logEvent(LogEvent.CONTENT_COLLECTION_SKIPPED)))
        .containsEntry("reasonCode", "PREVIOUS_RUN_ACTIVE");
  }

  @Test
  void logsBusinessFailureWithErrorCode() {
    given(batchService.collectAllEnabled())
        .willThrow(new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND));

    new ContentCollectionScheduler(batchService).collect();

    ILoggingEvent log = logEvent(LogEvent.CONTENT_COLLECTION_FAILED);
    assertThat(log.getLevel()).isEqualTo(Level.WARN);
    assertThat(keyValues(log))
        .containsEntry("errorCode", ContentErrorCode.CONTENT_COLLECTION_SOURCE_NOT_FOUND.code());
  }

  @Test
  void logsUnexpectedFailureWithSanitizedStackTrace() {
    given(batchService.collectAllEnabled())
        .willThrow(new IllegalStateException("secret database value"));

    new ContentCollectionScheduler(batchService).collect();

    ILoggingEvent log = logEvent(LogEvent.CONTENT_COLLECTION_FAILED);
    assertThat(log.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValues(log))
        .containsEntry("errorType", IllegalStateException.class.getSimpleName());
    assertThat(log.getThrowableProxy().getClassName())
        .isEqualTo("org.grit.daynomy.common.logging.SanitizedLogException");
    assertThat(log.getThrowableProxy().getMessage()).doesNotContain("secret database value");
  }

  private ILoggingEvent logEvent(LogEvent event) {
    return appender.list.stream()
        .filter(loggingEvent -> event.code().equals(keyValues(loggingEvent).get("event")))
        .findFirst()
        .orElseThrow();
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
