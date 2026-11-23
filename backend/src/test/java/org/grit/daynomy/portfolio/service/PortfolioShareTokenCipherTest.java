package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PortfolioShareTokenCipherTest {

  private final PortfolioShareTokenCipher cipher =
      new PortfolioShareTokenCipher("test-portfolio-share-token-secret-32-bytes");

  @Test
  void encryptsWithRandomIvAndDecryptsTheOriginalToken() {
    String token = "portfolio-share-token";

    String first = cipher.encrypt(token);
    String second = cipher.encrypt(token);

    assertThat(first).startsWith("v1.").isNotEqualTo(second);
    assertThat(cipher.decrypt(first)).isEqualTo(token);
    assertThat(cipher.decrypt(second)).isEqualTo(token);
  }

  @Test
  void rejectsShortKeysAndTamperedCiphertext() {
    assertThatThrownBy(() -> new PortfolioShareTokenCipher("short-secret"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cipher.decrypt("v1.invalid"))
        .isInstanceOf(IllegalStateException.class);
  }
}
