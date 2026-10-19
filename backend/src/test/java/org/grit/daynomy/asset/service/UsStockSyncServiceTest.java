package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.eodhd.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsStockSyncServiceTest {
  @Mock EodhdClient client;
  @Mock StockMasterPersistenceService masterPersistence;
  @Mock StockPricePersistenceService pricePersistence;
  @Mock AssetRepository assetRepository;
  @InjectMocks UsStockSyncService service;
  private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);

  @Test
  void importsUsdCommonStocksAndEtfsAcrossMajorExchanges() {
    given(client.getUsSymbols())
        .willReturn(
            List.of(
                symbol("AAPL", "NASDAQ", "Common Stock"), symbol("VTI", "NYSE ARCA", "ETF"),
                symbol("XYZ", "PINK", "Common Stock"), symbol("ABC-P", "NYSE", "Preferred Stock"),
                symbol("FUND", "NASDAQ", "Mutual Fund"), symbol("BRK-B", "NYSE", "Common Stock"),
                symbol("ETF2", "BATS", "ETF"), symbol("SMALL", "NYSE MKT", "Common Stock")));
    service.synchronizeMaster();
    ArgumentCaptor<List<StockMasterEntry>> captor = ArgumentCaptor.captor();
    verify(masterPersistence).synchronizeUs(any(), captor.capture());
    assertThat(captor.getValue())
        .extracting(StockMasterEntry::code)
        .containsExactly("AAPL.US", "VTI.US", "BRK-B.US", "ETF2.US", "SMALL.US");
    assertThat(captor.getValue().get(1).market()).isEqualTo(StockMarket.NYSE_ARCA);
    verifyNoInteractions(pricePersistence);
  }

  @Test
  void invalidOrPartialMasterNeverDelistsExistingAssetsAndReleasesLock() {
    given(client.getUsSymbols()).willReturn(List.of(symbol("AAPL", "NASDAQ", "Common Stock")));
    assertThatThrownBy(service::synchronizeMaster).isInstanceOf(BusinessException.class);
    verifyNoInteractions(masterPersistence);
    given(client.getUsSymbols())
        .willReturn(
            List.of(symbol("AAPL", "NASDAQ", "Common Stock"), symbol("VTI", "NYSE ARCA", "ETF")));
    service.synchronizeMaster();
    verify(masterPersistence).synchronizeUs(any(), anyList());
  }

  @Test
  void searchesPastWeekendWithoutWritingDomesticCalendarOrUnknownPrices() {
    given(assetRepository.findAllByCategoryIn(anySet())).willReturn(List.of(usAsset()));
    given(client.getUsDailyPrices(FRIDAY.plusDays(2))).willReturn(List.of());
    given(client.getUsDailyPrices(FRIDAY.plusDays(1))).willReturn(List.of());
    given(client.getUsDailyPrices(FRIDAY))
        .willReturn(List.of(price("AAPL", FRIDAY, "245.1234"), price("OTC", FRIDAY, "1.00")));
    service.synchronizePrices(null, FRIDAY.plusDays(3));
    verify(pricePersistence)
        .synchronizeUs(
            FRIDAY,
            List.of(
                new StockPriceEntry(
                    "AAPL.US", AssetCategory.STOCK, FRIDAY, new BigDecimal("245.1234"))));
    verify(pricePersistence, never()).synchronize(any(), any());
  }

  @Test
  void refusesWrongDateRatherThanSavingStalePrice() {
    given(assetRepository.findAllByCategoryIn(anySet())).willReturn(List.of(usAsset()));
    given(client.getUsDailyPrices(FRIDAY))
        .willReturn(List.of(price("AAPL", FRIDAY.minusDays(1), "245")));
    assertThatThrownBy(() -> service.synchronizePrices(FRIDAY, FRIDAY.plusDays(1)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(pricePersistence);
  }

  @Test
  void neverStoresZeroOrNullPrices() {
    given(assetRepository.findAllByCategoryIn(anySet())).willReturn(List.of(usAsset()));
    given(client.getUsDailyPrices(FRIDAY)).willReturn(List.of(price("AAPL", FRIDAY, "0")));
    assertThatThrownBy(() -> service.synchronizePrices(FRIDAY, FRIDAY.plusDays(1)))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(pricePersistence);
  }

  @Test
  void explicitDateDoesNotSilentlyFallBackOnHoliday() {
    given(assetRepository.findAllByCategoryIn(anySet())).willReturn(List.of(usAsset()));
    given(client.getUsDailyPrices(FRIDAY)).willReturn(List.of());
    assertThatThrownBy(() -> service.synchronizePrices(FRIDAY, FRIDAY.plusDays(1)))
        .isInstanceOf(BusinessException.class);
    verify(client).getUsDailyPrices(FRIDAY);
    verifyNoMoreInteractions(client);
  }

  @Test
  void rejectsTodayAndFutureBeforeExternalRequest() {
    assertThatThrownBy(() -> service.synchronizePrices(FRIDAY, FRIDAY))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.errorCode()).isEqualTo(AssetErrorCode.INVALID_US_STOCK_PRICE_DATE));
    verifyNoInteractions(client, assetRepository, pricePersistence);
  }

  @Test
  void requiresMasterBeforeFetchingBulkPrices() {
    given(assetRepository.findAllByCategoryIn(anySet())).willReturn(List.of());
    assertThatThrownBy(() -> service.synchronizePrices(FRIDAY, FRIDAY.plusDays(1)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.errorCode()).isEqualTo(AssetErrorCode.US_STOCK_MASTER_REQUIRED));
    verifyNoInteractions(client);
  }

  @Test
  void rejectsConcurrentSyncAndReleasesLockAfterFailure() {
    given(client.getUsSymbols())
        .willAnswer(
            invocation -> {
              assertThatThrownBy(service::synchronizeMaster)
                  .isInstanceOfSatisfying(
                      BusinessException.class,
                      e ->
                          assertThat(e.errorCode())
                              .isEqualTo(AssetErrorCode.US_STOCK_SYNC_ALREADY_RUNNING));
              return List.of(
                  symbol("AAPL", "NASDAQ", "Common Stock"), symbol("VTI", "NYSE ARCA", "ETF"));
            });
    service.synchronizeMaster();
    service.synchronizeMaster();
    verify(masterPersistence, times(2)).synchronizeUs(any(), anyList());
  }

  private EodhdSymbol symbol(String code, String exchange, String type) {
    return new EodhdSymbol(code, code, exchange, "USD", type, null);
  }

  private Asset usAsset() {
    return Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, FRIDAY);
  }

  private EodhdDailyPrice price(String code, LocalDate date, String close) {
    return new EodhdDailyPrice(code, "US", date, new BigDecimal(close));
  }
}
