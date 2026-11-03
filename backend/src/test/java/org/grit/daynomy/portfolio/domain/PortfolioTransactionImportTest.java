package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioTransactionImportTest {
  private PortfolioTransactionImport.Entry entry(String price, BigDecimal rate) {
    return new PortfolioTransactionImport.Entry(
        1L,
        PortfolioTransactionType.BUY,
        LocalDate.of(2026, 1, 1),
        2L,
        new BigDecimal(price),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        rate);
  }

  @Test
  void numericFormattingAndDomesticDefaultRateDoNotBypassDeduplication() {
    assertThat(PortfolioTransactionImport.fingerprint(List.of(entry("100", null))))
        .isEqualTo(
            PortfolioTransactionImport.fingerprint(List.of(entry("100.0000", BigDecimal.ONE))));
    assertThat(PortfolioTransactionImport.fingerprint(List.of(entry("101", null))))
        .isNotEqualTo(PortfolioTransactionImport.fingerprint(List.of(entry("100", null))));
  }

  @Test
  void sameDateRowOrderAndRepeatedTransactionsArePreserved() {
    assertThat(
            PortfolioTransactionImport.fingerprint(List.of(entry("100", null), entry("101", null))))
        .isNotEqualTo(
            PortfolioTransactionImport.fingerprint(
                List.of(entry("101", null), entry("100", null))));
    assertThat(
            PortfolioTransactionImport.fingerprint(List.of(entry("100", null), entry("100", null))))
        .isNotEqualTo(PortfolioTransactionImport.fingerprint(List.of(entry("100", null))));
  }
}
