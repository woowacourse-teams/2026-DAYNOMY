package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.junit.jupiter.api.Test;

class AssetMentionMatcherTest {

  private final AssetMentionMatcher matcher = new AssetMentionMatcher();

  @Test
  void matchesExactKoreanAndEnglishAssetNames() {
    Asset samsung = new Asset("삼성전자", AssetCategory.STOCK, "005930");
    Asset apple = new Asset("Apple", AssetCategory.STOCK, "AAPL");

    assertThat(
            matcher.match(
                "삼성전자 실적 발표", "Apple also reported quarterly results.", List.of(samsung, apple)))
        .containsExactly(samsung, apple);
  }

  @Test
  void doesNotMatchEnglishNameInsideAnotherWord() {
    Asset meta = new Asset("Meta", AssetCategory.STOCK, "META");

    assertThat(matcher.match("Metaverse 시장 전망", "", List.of(meta))).isEmpty();
  }

  @Test
  void matchesExactStockTickerWithoutMatchingInsideAnotherToken() {
    Asset apple = new Asset("Apple", AssetCategory.STOCK, "AAPL");

    assertThat(matcher.match("$AAPL 실적 전망", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("XAAPLX는 종목 코드가 아니다", "", List.of(apple))).isEmpty();
  }

  @Test
  void matchesUsAssetByTickerWithoutCountrySuffix() {
    Asset apple = usStock("Apple Inc", "AAPL.US");

    assertThat(matcher.match("$AAPL 실적 전망", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("XAAPLX는 종목 코드가 아니다", "", List.of(apple))).isEmpty();
  }

  @Test
  void shortUsTickersRequireCashtag() {
    Asset on = usStock("ON Semiconductor Corporation", "ON.US");
    Asset ford = usStock("Ford Motor Company", "F.US");

    assertThat(matcher.match("update on inflation", "", List.of(on))).isEmpty();
    assertThat(matcher.match("F reports earnings", "", List.of(ford))).isEmpty();
    assertThat(matcher.match("$ON and $F", "", List.of(on, ford))).containsExactly(on, ford);
  }

  @Test
  void matchesUsAssetByKoreanNameAndEnglishNameWithoutCompanySuffix() {
    Asset apple = usStock("Apple Inc.", "AAPL.US");
    apple.synchronizeKoreanName("애플");

    assertThat(matcher.match("Apple 신제품 공개", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("애플 실적 발표", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("애플은 실적을 발표했다", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("애플에서는 신제품을 발표했다", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("애플에게도 영향을 준다", "", List.of(apple))).containsExactly(apple);
    assertThat(matcher.match("Pineapple 시장 성장", "", List.of(apple))).isEmpty();
    assertThat(matcher.match("파인애플 시장 성장", "", List.of(apple))).isEmpty();
  }

  private Asset usStock(String name, String code) {
    return Asset.listedStock(name, code, StockMarket.NASDAQ, null, LocalDate.of(2026, 11, 4));
  }
}
