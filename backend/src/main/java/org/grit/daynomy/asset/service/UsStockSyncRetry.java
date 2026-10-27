package org.grit.daynomy.asset.service;

import java.sql.SQLException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.RetryableExternalRequestException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsStockSyncRetry {
  private final UsStockSyncRetryProperties properties;

  public <T> T download(Supplier<T> operation) {
    return execute(operation, exception -> exception instanceof RetryableExternalRequestException);
  }

  public <T> T persist(Supplier<T> operation) {
    return execute(operation, this::transientDatabaseFailure);
  }

  private <T> T execute(Supplier<T> operation, Predicate<RuntimeException> retryable) {
    for (int attempt = 1; ; attempt++) {
      try {
        return operation.get();
      } catch (RuntimeException exception) {
        if (attempt >= properties.maxAttempts() || !retryable.test(exception)) throw exception;
        try {
          Thread.sleep(properties.delay().multipliedBy(attempt));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new BusinessException(AssetErrorCode.US_STOCK_SYNC_INTERRUPTED);
        }
      }
    }
  }

  private boolean transientDatabaseFailure(RuntimeException exception) {
    if (exception instanceof TransientDataAccessException) return true;
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql) {
        String state = sql.getSQLState();
        if (state != null
            && (state.equals("40001") || state.equals("40P01") || state.startsWith("08")))
          return true;
      }
    }
    return false;
  }
}
