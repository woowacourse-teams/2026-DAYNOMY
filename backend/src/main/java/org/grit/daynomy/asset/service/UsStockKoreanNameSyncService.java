package org.grit.daynomy.asset.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.kis.KisStockMasterClient;
import org.grit.daynomy.external.kis.KisStockMasterParser;
import org.grit.daynomy.external.kis.dto.KisStockName;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsStockKoreanNameSyncService {
  private final KisStockMasterClient client;
  private final KisStockMasterParser parser;
  private final UsStockKoreanNamePersistenceService persistenceService;
  private final UsStockSyncRetry retry;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public UsStockKoreanNameSyncResult synchronize() {
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(AssetErrorCode.US_STOCK_KOREAN_NAME_SYNC_ALREADY_RUNNING);
    }
    try {
      List<KisStockName> entries = new ArrayList<>();
      for (String market : KisStockMasterClient.US_MARKETS) {
        byte[] archive = retry.download(() -> client.download(market));
        entries.addAll(parser.parse(market, archive));
      }
      List<KisStockName> snapshot = List.copyOf(entries);
      return retry.persist(() -> persistenceService.synchronize(snapshot));
    } finally {
      running.set(false);
    }
  }
}
