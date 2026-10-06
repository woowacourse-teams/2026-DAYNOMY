package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient;
import org.grit.daynomy.investmentcalendar.external.InvestmentCalendarHistoricalPriceClient.HistoricalPrice;
import org.grit.daynomy.investmentcalendar.repository.InvestmentCalendarHoldingAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentCalendarPriceBackfillServiceTest {

  @Mock InvestmentCalendarHoldingAssetRepository holdingAssetRepository;
  @Mock StockDailyPriceRepository priceRepository;
  @Mock InvestmentCalendarHistoricalPriceClient priceClient;

  private InvestmentCalendarPriceBackfillService service;

  @BeforeEach
  void setUp() {
    service =
        new InvestmentCalendarPriceBackfillService(
            holdingAssetRepository, priceRepository, priceClient);
    ReflectionTestUtils.setField(service, "historyYears", 5);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createsMissingHistoricalPricesAndUpdatesChangedPrices() {
    LocalDate today = LocalDate.of(2026, 10, 7);
    Asset asset = asset();
    StockDailyPrice stored =
        new StockDailyPrice(asset, LocalDate.of(2026, 10, 1), new BigDecimal("68000"));
    given(holdingAssetRepository.findDistinctListedAssetsByCategories(anySet()))
        .willReturn(List.of(asset));
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                List.of(1L), today.minusYears(5), today))
        .willReturn(List.of(stored));
    given(priceClient.fetch(asset.getCategory(), asset.getAssetCode(), today.minusYears(5), today))
        .willReturn(
            List.of(
                new HistoricalPrice(LocalDate.of(2026, 9, 30), new BigDecimal("67000")),
                new HistoricalPrice(LocalDate.of(2026, 10, 1), new BigDecimal("69000"))));

    InvestmentCalendarPriceBackfillResult result = service.backfill(today);

    assertThat(result).isEqualTo(new InvestmentCalendarPriceBackfillResult(1, 2, 1, 1, 0));
    ArgumentCaptor<List<StockDailyPrice>> captor = ArgumentCaptor.forClass(List.class);
    then(priceRepository).should().saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(stored.getClosePrice()).isEqualByComparingTo("69000");
  }

  @Test
  void skipsAssetWhenHistoricalRangeIsAlreadyCurrent() {
    LocalDate today = LocalDate.of(2026, 10, 7);
    Asset asset = asset();
    List<StockDailyPrice> existing =
        List.of(
            new StockDailyPrice(asset, today.minusYears(5).plusDays(3), new BigDecimal("50000")),
            new StockDailyPrice(asset, today, new BigDecimal("69000")));
    given(holdingAssetRepository.findDistinctListedAssetsByCategories(anySet()))
        .willReturn(List.of(asset));
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                List.of(1L), today.minusYears(5), today))
        .willReturn(existing);

    InvestmentCalendarPriceBackfillResult result = service.backfill(today);

    assertThat(result).isEqualTo(new InvestmentCalendarPriceBackfillResult(1, 0, 0, 0, 0));
    then(priceClient).shouldHaveNoInteractions();
    then(priceRepository).shouldHaveNoMoreInteractions();
  }

  private Asset asset() {
    Asset asset =
        Asset.listedStock(
            "삼성전자", "005930", StockMarket.KOSPI, "KR7005930003", LocalDate.of(2026, 10, 7));
    ReflectionTestUtils.setField(asset, "id", 1L);
    return asset;
  }
}
