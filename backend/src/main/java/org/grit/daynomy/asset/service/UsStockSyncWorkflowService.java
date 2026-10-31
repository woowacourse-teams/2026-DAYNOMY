package org.grit.daynomy.asset.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsStockSyncWorkflowService {
  private final UsStockSyncService stocks;
  private final UsStockKoreanNameSyncService koreanNames;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public UsStockSyncResult synchronize() {
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(AssetErrorCode.US_STOCK_SYNC_ALREADY_RUNNING);
    }
    try {
      StockSyncResult master = stocks.synchronizeMaster();
      try {
        return new UsStockSyncResult(master, koreanNames.synchronize(), null);
      } catch (BusinessException exception) {
        if (exception.errorCode() == AssetErrorCode.US_STOCK_SYNC_INTERRUPTED) throw exception;
        return failedNames(master, exception.errorCode().code());
      } catch (DataAccessException | TransactionException exception) {
        return failedNames(master, AssetErrorCode.US_STOCK_KOREAN_NAME_SYNC_FAILED.code());
      }
    } finally {
      running.set(false);
    }
  }

  private UsStockSyncResult failedNames(StockSyncResult master, String code) {
    log.warn("US stock master committed; Korean name sync failed: code={}", code);
    return new UsStockSyncResult(master, null, code);
  }
}
