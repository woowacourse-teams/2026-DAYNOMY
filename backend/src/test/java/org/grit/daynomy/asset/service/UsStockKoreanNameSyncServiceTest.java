package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.archive;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.row;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.RetryableExternalRequestException;
import org.grit.daynomy.external.kis.KisStockMasterClient;
import org.grit.daynomy.external.kis.KisStockMasterParser;
import org.grit.daynomy.external.kis.dto.KisStockName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class UsStockKoreanNameSyncServiceTest {
  @Mock KisStockMasterClient client;
  @Spy KisStockMasterParser parser = new KisStockMasterParser();
  @Mock UsStockKoreanNamePersistenceService persistence;

  @Spy
  UsStockSyncRetry retry = new UsStockSyncRetry(new UsStockSyncRetryProperties(3, Duration.ZERO));

  @InjectMocks UsStockKoreanNameSyncService service;

  private final byte[] nas = archive("NASMST.COD", row("NAS", "AAPL", "애플", "2")).readByteArray();
  private final byte[] nys = archive("NYSMST.COD", row("NYS", "IBM", "아이비엠", "2")).readByteArray();
  private final byte[] ams =
      archive("AMSMST.COD", row("AMS", "SPY", "에스피 ETF", "3")).readByteArray();
  private final List<KisStockName> entries =
      List.of(
          new KisStockName("NAS", "AAPL", "애플", "2"),
          new KisStockName("NYS", "IBM", "아이비엠", "2"),
          new KisStockName("AMS", "SPY", "에스피 ETF", "3"));
  private final UsStockKoreanNameSyncResult result = new UsStockKoreanNameSyncResult(3, 3, 3, 0, 0);

  @Test
  void retriesOnlyPersistenceUsingAlreadyParsedSnapshot() {
    downloads();
    given(persistence.synchronize(entries))
        .willThrow(new CannotAcquireLockException("temporary"))
        .willReturn(result);
    assertThat(service.synchronize()).isEqualTo(result);
    for (String market : KisStockMasterClient.US_MARKETS) verify(client).download(market);
    verify(parser).parse("nas", nas);
    verify(parser).parse("nys", nys);
    verify(parser).parse("ams", ams);
    verify(persistence, times(2)).synchronize(entries);
  }

  @Test
  void retriesOnlyFailedMarketWithoutDownloadingOrParsingSuccessfulMarketAgain() {
    downloads();
    given(client.download("nys")).willThrow(transientFailure()).willReturn(nys);
    given(persistence.synchronize(entries)).willReturn(result);
    assertThat(service.synchronize()).isEqualTo(result);
    var order = inOrder(client, parser, persistence);
    order.verify(client).download("nas");
    order.verify(parser).parse("nas", nas);
    order.verify(client, times(2)).download("nys");
    order.verify(parser).parse("nys", nys);
    order.verify(client).download("ams");
    order.verify(parser).parse("ams", ams);
    order.verify(persistence).synchronize(entries);
    verifyNoMoreInteractions(client, parser, persistence);
  }

  @Test
  void exhaustedMarketNeverParsesFailedFileOrStartsPersistence() {
    given(client.download("nas")).willReturn(nas);
    given(client.download("nys")).willThrow(transientFailure());
    assertThatThrownBy(service::synchronize).isInstanceOf(RetryableExternalRequestException.class);
    verify(client).download("nas");
    verify(client, times(3)).download("nys");
    verify(parser).parse("nas", nas);
    verifyNoMoreInteractions(client, parser);
    verifyNoInteractions(persistence);
  }

  @Test
  void malformedFileFailsWithoutRepeatingDownloadOrParse() {
    byte[] invalid = archive("NYSMST.COD", "US\tNYS\tIBM").readByteArray();
    given(client.download("nas")).willReturn(nas);
    given(client.download("nys")).willReturn(invalid);
    assertThatThrownBy(service::synchronize)
        .isInstanceOf(BusinessException.class)
        .isNotInstanceOf(RetryableExternalRequestException.class);
    verify(client).download("nas");
    verify(client).download("nys");
    verify(parser).parse("nas", nas);
    verify(parser).parse("nys", invalid);
    verifyNoMoreInteractions(client, parser);
    verifyNoInteractions(persistence);
  }

  @Test
  void downloadFailureNeverStartsPersistenceAndNextExecutionDownloadsFreshData() {
    given(client.download("nas"))
        .willThrow(new BusinessException(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED))
        .willReturn(nas);
    assertThatThrownBy(service::synchronize).isInstanceOf(BusinessException.class);
    verifyNoInteractions(parser, persistence);
    given(client.download("nys")).willReturn(nys);
    given(client.download("ams")).willReturn(ams);
    given(persistence.synchronize(entries)).willReturn(result);
    assertThat(service.synchronize()).isEqualTo(result);
    verify(client, times(2)).download("nas");
  }

  @Test
  void rejectsOverlapAndDoesNotCacheDownloadsBetweenExecutions() {
    given(client.download("nas"))
        .willAnswer(
            invocation -> {
              assertThatThrownBy(service::synchronize)
                  .isInstanceOfSatisfying(
                      BusinessException.class,
                      exception ->
                          assertThat(exception.errorCode())
                              .isEqualTo(AssetErrorCode.US_STOCK_KOREAN_NAME_SYNC_ALREADY_RUNNING));
              return nas;
            });
    given(client.download("nys")).willReturn(nys);
    given(client.download("ams")).willReturn(ams);
    given(persistence.synchronize(entries))
        .willThrow(new BusinessException(AssetErrorCode.US_STOCK_MASTER_REQUIRED))
        .willReturn(result);
    assertThatThrownBy(service::synchronize)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AssetErrorCode.US_STOCK_MASTER_REQUIRED));
    assertThat(service.synchronize()).isEqualTo(result);
    for (String market : KisStockMasterClient.US_MARKETS) verify(client, times(2)).download(market);
  }

  private void downloads() {
    given(client.download("nas")).willReturn(nas);
    given(client.download("nys")).willReturn(nys);
    given(client.download("ams")).willReturn(ams);
  }

  private RetryableExternalRequestException transientFailure() {
    return new RetryableExternalRequestException(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
  }
}
