package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.RetryableExternalRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

class UsStockSyncRetryTest {
  private final UsStockSyncRetry retry =
      new UsStockSyncRetry(new UsStockSyncRetryProperties(3, Duration.ZERO));

  @Test
  void retriesTransientRequestOnlyUpToConfiguredLimit() {
    AtomicInteger attempts = new AtomicInteger();
    assertThatThrownBy(
            () ->
                retry.download(
                    () -> {
                      attempts.incrementAndGet();
                      throw new RetryableExternalRequestException(
                          ExternalErrorCode.EODHD_API_REQUEST_FAILED);
                    }))
        .isInstanceOf(BusinessException.class);
    assertThat(attempts.get()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"40001", "40P01", "08006"})
  void retriesTransientDatabaseStatesAndReturnsSuccessfulAttempt(String state) {
    AtomicInteger attempts = new AtomicInteger();
    assertThat(
            retry.persist(
                () -> {
                  if (attempts.incrementAndGet() == 1)
                    throw new IllegalStateException(new SQLException("temporary", state));
                  return "saved";
                }))
        .isEqualTo("saved");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void retriesTransientConnectionWithoutSqlStateAndReturnsSuccessfulAttempt() {
    AtomicInteger attempts = new AtomicInteger();
    assertThat(
            retry.persist(
                () -> {
                  if (attempts.incrementAndGet() == 1)
                    throw new CannotCreateTransactionException(
                        "connection unavailable", new SQLTransientConnectionException("temporary"));
                  return "saved";
                }))
        .isEqualTo("saved");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void doesNotRetryInvalidDataOrOrdinaryUpstreamFailure() {
    for (RuntimeException failure :
        new RuntimeException[] {
          new BusinessException(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED),
          new DataIntegrityViolationException(
              "constraint", new SQLException("constraint", "23505")),
          new IllegalArgumentException("bad input")
        }) {
      AtomicInteger attempts = new AtomicInteger();
      assertThatThrownBy(
              () ->
                  retry.persist(
                      () -> {
                        attempts.incrementAndGet();
                        throw failure;
                      }))
          .isSameAs(failure);
      assertThat(attempts.get()).isEqualTo(1);
    }
  }

  @Test
  void downloadAndPersistenceDoNotRetryEachOthersFailures() {
    AtomicInteger downloads = new AtomicInteger();
    assertThatThrownBy(
            () ->
                retry.download(
                    () -> {
                      downloads.incrementAndGet();
                      throw new org.springframework.dao.CannotAcquireLockException(
                          "database failure");
                    }))
        .isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
    assertThat(downloads.get()).isEqualTo(1);

    AtomicInteger saves = new AtomicInteger();
    assertThatThrownBy(
            () ->
                retry.persist(
                    () -> {
                      saves.incrementAndGet();
                      throw new RetryableExternalRequestException(
                          ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
                    }))
        .isInstanceOf(RetryableExternalRequestException.class);
    assertThat(saves.get()).isEqualTo(1);
  }

  @Test
  void interruptionStopsRetriesAndRestoresThreadFlag() {
    var waiting = new UsStockSyncRetry(new UsStockSyncRetryProperties(3, Duration.ofSeconds(1)));
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(
              () ->
                  waiting.download(
                      () -> {
                        throw new RetryableExternalRequestException(
                            ExternalErrorCode.EODHD_API_REQUEST_FAILED);
                      }))
          .isInstanceOf(BusinessException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
