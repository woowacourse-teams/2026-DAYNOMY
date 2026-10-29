package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.kis.dto.KisStockName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsStockKoreanNamePersistenceServiceTest {
  @Mock AssetRepository assets;
  @InjectMocks UsStockKoreanNamePersistenceService service;

  @Test
  void matchesMarketTickerAndTypeWithoutChangingOriginalOrDomesticData() {
    Asset apple = stock("AAPL.US", StockMarket.NASDAQ);
    Asset berkshire = stock("BRK-B.US", StockMarket.NYSE);
    Asset korean = stock("AAPL", StockMarket.KOSPI);
    Asset wrongMarket = stock("WRONG.US", StockMarket.NYSE);
    Asset delisted = stock("DELISTED.US", StockMarket.NASDAQ);
    delisted.delist();
    Asset etf =
        Asset.listedSecurity(
            "Original ETF",
            AssetCategory.ETF,
            "VTI.US",
            StockMarket.NYSE_ARCA,
            null,
            LocalDate.now());
    stored(apple, berkshire, korean, wrongMarket, delisted, etf);

    var entries =
        List.of(
            name("NAS", "AAPL", "애플", "2"),
            name("NYS", "BRK/B", "버크셔 해서웨이 B", "2"),
            name("AMS", "VTI", "뱅가드 ETF", "3"),
            name("NAS", "WRONG", "시장 불일치", "2"),
            name("NAS", "DELISTED", "상장폐지", "2"),
            name("NAS", "MISSING", "없는 종목", "2"),
            name("AMS", "VTI", "유형 불일치", "2"),
            name("NAS", "AAPL", "APPLE", "2"),
            name("NAS", "AAPL", "", "2"));

    assertThat(service.synchronize(entries))
        .isEqualTo(new UsStockKoreanNameSyncResult(9, 3, 3, 4, 2));
    assertThat(apple.getNameKo()).isEqualTo("애플");
    assertThat(berkshire.getNameKo()).isEqualTo("버크셔 해서웨이 B");
    assertThat(etf.getNameKo()).isEqualTo("뱅가드 ETF");
    assertThat(apple.getName()).isEqualTo("Original AAPL.US");
    assertThat(korean.getNameKo()).isNull();
    assertThat(wrongMarket.getNameKo()).isNull();
    assertThat(delisted.getNameKo()).isNull();
    verify(assets).saveAll(List.of(apple, berkshire, etf));
    assertThat(service.synchronize(entries).updatedCount()).isZero();
  }

  @Test
  void preservesExistingNamesWhenMissingFromFeedOrWithoutKoreanTranslation() {
    Asset apple = stock("AAPL.US", StockMarket.NASDAQ);
    Asset missing = stock("MISS.US", StockMarket.NASDAQ);
    apple.synchronizeKoreanName("기존 애플");
    missing.synchronizeKoreanName("기존 이름");
    stored(apple, missing);

    assertThat(service.synchronize(List.of(name("NAS", "AAPL", "Apple", "2"))))
        .isEqualTo(new UsStockKoreanNameSyncResult(1, 0, 0, 0, 1));
    assertThat(apple.getNameKo()).isEqualTo("기존 애플");
    assertThat(missing.getNameKo()).isEqualTo("기존 이름");
    assertThat(missing.isListed()).isTrue();
  }

  @Test
  void skipsAmbiguousShareClassTickerVariants() {
    Asset dash = stock("BRK-B.US", StockMarket.NYSE);
    Asset dot = stock("BRK.B.US", StockMarket.NYSE);
    stored(dash, dot);

    assertThat(service.synchronize(List.of(name("NYS", "BRK/B", "버크셔 B", "2"))))
        .isEqualTo(new UsStockKoreanNameSyncResult(1, 0, 0, 1, 0));
    assertThat(dash.getNameKo()).isNull();
    assertThat(dot.getNameKo()).isNull();
  }

  @Test
  void rejectsConflictingNamesBeforeChangingAnyAsset() {
    Asset apple = stock("AAPL.US", StockMarket.NASDAQ);
    Asset berkshire = stock("BRK-B.US", StockMarket.NYSE);
    stored(apple, berkshire);

    assertThatThrownBy(
            () ->
                service.synchronize(
                    List.of(
                        name("NAS", "AAPL", "애플", "2"),
                        name("NYS", "BRK-B", "버크셔 B", "2"),
                        name("NYS", "BRK/B", "다른 이름", "2"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AssetErrorCode.US_STOCK_KOREAN_NAME_DATA_INVALID));
    assertThat(apple.getNameKo()).isNull();
    assertThat(berkshire.getNameKo()).isNull();
    verify(assets, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void requiresExistingListedUsMaster() {
    stored(stock("005930", StockMarket.KOSPI));

    assertThatThrownBy(() -> service.synchronize(List.of(name("NAS", "AAPL", "애플", "2"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AssetErrorCode.US_STOCK_MASTER_REQUIRED));
  }

  private Asset stock(String code, StockMarket market) {
    return Asset.listedStock("Original " + code, code, market, null, LocalDate.of(2026, 10, 27));
  }

  private KisStockName name(String market, String symbol, String name, String type) {
    return new KisStockName(market, symbol, name, type);
  }

  private void stored(Asset... existing) {
    given(assets.findAllByCategoryIn(Set.of(AssetCategory.STOCK, AssetCategory.ETF)))
        .willReturn(List.of(existing));
  }
}
