package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class UsEconomyNewsGenerationSchedulerTest {

  @Mock private NewsGenerationService newsGenerationService;

  @InjectMocks private UsEconomyNewsGenerationScheduler scheduler;

  private final Logger logger =
      (Logger) LoggerFactory.getLogger(UsEconomyNewsGenerationScheduler.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @BeforeEach
  void setUpLogging() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogging() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void scheduledUsGenerationTriggersOnlyUsNews() {
    scheduler.generateUsEconomicNewsDrafts();

    verify(newsGenerationService).generateUsEconomicNewsDrafts();
  }

  @Test
  void logsGenerationFailureAtWarnLevel() {
    given(newsGenerationService.generateUsEconomicNewsDrafts())
        .willThrow(new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));

    scheduler.generateUsEconomicNewsDrafts();

    assertThat(appender.list).hasSize(2);
    ILoggingEvent log = appender.list.get(1);
    assertThat(log.getLevel()).isEqualTo(Level.WARN);
    assertThat(log.getFormattedMessage()).isEqualTo(LogEvent.NEWS_GENERATION_FAILED.message());
    assertThat(keyValues(log))
        .containsEntry("event", LogEvent.NEWS_GENERATION_FAILED.code())
        .containsEntry("generationType", "us")
        .containsEntry("errorCode", NewsErrorCode.NEWS_NOT_FOUND.code());
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
