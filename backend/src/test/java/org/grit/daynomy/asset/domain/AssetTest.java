package org.grit.daynomy.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AssetTest {
  @Test
  void koreanNameIsSeparateAndSurvivesMasterSynchronization() {
    Asset apple =
        Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, LocalDate.of(2026, 10, 27));

    assertThat(apple.synchronizeKoreanName(" 애플 ")).isTrue();
    assertThat(apple.getName()).isEqualTo("Apple");
    assertThat(apple.synchronizeKoreanName("애플")).isFalse();
    apple.synchronizeStock("Apple Inc.", StockMarket.NASDAQ, null, LocalDate.of(2026, 10, 28));
    assertThat(apple.getNameKo()).isEqualTo("애플");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsClearingExistingKoreanName(String name) {
    Asset apple = new Asset("Apple", AssetCategory.STOCK, "AAPL.US");
    apple.synchronizeKoreanName("애플");

    assertThatThrownBy(() -> apple.synchronizeKoreanName(name))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(apple.getNameKo()).isEqualTo("애플");
  }
}
