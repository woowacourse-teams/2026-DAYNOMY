package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockMasterPersistenceServiceTest {

  @Mock private AssetRepository assetRepository;
  @InjectMocks private StockMasterPersistenceService persistenceService;

  @Test
  @DisplayName("신규 종목을 추가하고 기존 종목을 갱신하며 누락된 상장 종목을 비활성화한다")
  void synchronizeStockMaster() {
    LocalDate previousDate = LocalDate.of(2026, 9, 17);
    LocalDate baseDate = LocalDate.of(2026, 9, 18);
    Asset samsung =
        Asset.listedStock("삼성전자 구명칭", "005930", StockMarket.KOSPI, "KR7005930003", previousDate);
    Asset delisted =
        Asset.listedStock("상장폐지종목", "123450", StockMarket.KOSDAQ, "KR7123450009", previousDate);
    given(assetRepository.findAllByCategoryIn(Set.of(AssetCategory.STOCK, AssetCategory.ETF)))
        .willReturn(List.of(samsung, delisted));

    StockSyncResult result =
        persistenceService.synchronize(
            baseDate,
            List.of(
                new StockMasterEntry(
                    "005930",
                    "삼성전자",
                    AssetCategory.STOCK,
                    StockMarket.KOSPI,
                    "KR7005930003",
                    baseDate),
                new StockMasterEntry(
                    "000660",
                    "SK하이닉스",
                    AssetCategory.STOCK,
                    StockMarket.KOSPI,
                    "KR7000660001",
                    baseDate),
                new StockMasterEntry(
                    "069500",
                    "KODEX 200",
                    AssetCategory.ETF,
                    StockMarket.KOSPI,
                    "KR7069500007",
                    baseDate)));

    assertThat(result).isEqualTo(new StockSyncResult(baseDate, 3, 2, 1, 1));
    assertThat(samsung.getName()).isEqualTo("삼성전자");
    assertThat(samsung.isListed()).isTrue();
    assertThat(delisted.isListed()).isFalse();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Asset>> assetsCaptor = ArgumentCaptor.forClass(List.class);
    then(assetRepository).should().saveAll(assetsCaptor.capture());
    assertThat(assetsCaptor.getValue())
        .hasSize(4)
        .extracting(Asset::getAssetCode)
        .containsExactly("005930", "000660", "069500", "123450");
  }

  @Test
  @DisplayName("ETF 데이터가 없는 주식 동기화에서는 기존 ETF를 비활성화하지 않는다")
  void synchronizeOnlyCategoriesIncludedInEntries() {
    LocalDate previousDate = LocalDate.of(2026, 9, 17);
    LocalDate baseDate = LocalDate.of(2026, 9, 18);
    Asset missingStock =
        Asset.listedStock("누락 주식", "123450", StockMarket.KOSDAQ, "KR7123450009", previousDate);
    Asset existingEtf =
        Asset.listedSecurity(
            "KODEX 200",
            AssetCategory.ETF,
            "069500",
            StockMarket.KOSPI,
            "KR7069500007",
            previousDate);
    given(assetRepository.findAllByCategoryIn(Set.of(AssetCategory.STOCK, AssetCategory.ETF)))
        .willReturn(List.of(missingStock, existingEtf));

    StockSyncResult result =
        persistenceService.synchronize(
            baseDate,
            List.of(
                new StockMasterEntry(
                    "005930",
                    "삼성전자",
                    AssetCategory.STOCK,
                    StockMarket.KOSPI,
                    "KR7005930003",
                    baseDate)));

    assertThat(result).isEqualTo(new StockSyncResult(baseDate, 1, 1, 0, 1));
    assertThat(missingStock.isListed()).isFalse();
    assertThat(existingEtf.isListed()).isTrue();
  }

  @Test
  void domesticAndUsSyncNeverDelistEachOther() {
    LocalDate date = LocalDate.of(2026, 10, 16);
    Asset korean = Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, null, date);
    Asset apple = Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, date);
    Asset missingUs = Asset.listedStock("Missing", "MISS.US", StockMarket.NYSE, null, date);
    given(assetRepository.findAllByCategoryIn(Set.of(AssetCategory.STOCK, AssetCategory.ETF)))
        .willReturn(List.of(korean, apple, missingUs));
    persistenceService.synchronize(
        date,
        List.of(
            new StockMasterEntry(
                "005930", "삼성전자", AssetCategory.STOCK, StockMarket.KOSPI, null, date)));
    assertThat(apple.isListed()).isTrue();
    assertThat(missingUs.isListed()).isTrue();
    persistenceService.synchronizeUs(
        date,
        List.of(
            new StockMasterEntry(
                "AAPL.US", "Apple", AssetCategory.STOCK, StockMarket.NASDAQ, null, date)));
    assertThat(korean.isListed()).isTrue();
    assertThat(apple.isListed()).isTrue();
    assertThat(missingUs.isListed()).isFalse();
  }

  @Test
  void domesticSyncReusesAndDelistsLegacyAssetsWithoutMarket() {
    LocalDate date = LocalDate.of(2026, 10, 16);
    Asset legacy = Asset.listedStock("기존", "005930", StockMarket.KOSPI, null, date);
    Asset missing = Asset.listedStock("누락", "000660", StockMarket.KOSPI, null, date);
    org.springframework.test.util.ReflectionTestUtils.setField(legacy, "market", null);
    org.springframework.test.util.ReflectionTestUtils.setField(missing, "market", null);
    given(assetRepository.findAllByCategoryIn(Set.of(AssetCategory.STOCK, AssetCategory.ETF)))
        .willReturn(List.of(legacy, missing));
    var result =
        persistenceService.synchronize(
            date,
            List.of(
                new StockMasterEntry(
                    "005930", "삼성전자", AssetCategory.STOCK, StockMarket.KOSPI, null, date)));
    assertThat(result.createdCount()).isZero();
    assertThat(result.updatedCount()).isEqualTo(1);
    assertThat(result.delistedCount()).isEqualTo(1);
    assertThat(legacy.getMarket()).isEqualTo(StockMarket.KOSPI);
    assertThat(missing.isListed()).isFalse();
  }
}
