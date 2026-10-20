package org.grit.daynomy.common.logging;

import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionLog {
  private TransactionLog() {}

  public static void afterCommit(LogEvent event, LoggingEventBuilder builder) {
    Runnable write = () -> builder.addKeyValue("event", event.code()).log(event.message());
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      write.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            write.run();
          }
        });
  }
}
