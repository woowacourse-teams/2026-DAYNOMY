package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
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
}
