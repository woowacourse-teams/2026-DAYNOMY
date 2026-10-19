package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PortfolioSnapshotSchedulerTest {
  @Test
  void logsFailureWithoutSensitiveMessageAndRethrowsOriginalException() {
    var service = mock(PortfolioSnapshotService.class);
    var exception = new IllegalStateException("private-database-value");
    when(service.createLatestSnapshots()).thenThrow(exception).thenReturn(1);
    var scheduler = new PortfolioSnapshotScheduler(service);
    var logger = (Logger) LoggerFactory.getLogger(PortfolioSnapshotScheduler.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatThrownBy(scheduler::createSnapshots).isSameAs(exception);
      scheduler.createSnapshots();
      verify(service, times(2)).createLatestSnapshots();
      assertThat(appender.list).hasSize(1);
      var failure = appender.list.getFirst();
      assertThat(failure.getLevel()).isEqualTo(Level.ERROR);
      assertThat(failure.getKeyValuePairs().toString()).contains("portfolio.snapshot.failed");
      assertThat(ThrowableProxyUtil.asString(failure.getThrowableProxy()))
          .contains("IllegalStateException")
          .doesNotContain("private-database-value");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
