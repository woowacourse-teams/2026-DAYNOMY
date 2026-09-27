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
}
