package org.grit.daynomy.external.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.archive;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.row;

import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.kis.dto.KisStockName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KisStockMasterParserTest {
  private final KisStockMasterParser parser = new KisStockMasterParser();
  private byte[] content;

  @Test
  void readsFirstCp949RowAndCaseInsensitiveArchiveFilenameForEveryMarket() {
    assertThat(
            parser.parse(
                "nas",
                archive("NASMST.COD", row("NAS", "AAPL", "애플", "2") + "\r\n").readByteArray()))
        .containsExactly(new KisStockName("NAS", "AAPL", "애플", "2"));
    assertThat(
            parser.parse(
                "nys",
                archive("nysmst.cod", row("NYS", "BRK/B", "버크셔 해서웨이 B", "2")).readByteArray()))
        .containsExactly(new KisStockName("NYS", "BRK/B", "버크셔 해서웨이 B", "2"));
    assertThat(
            parser.parse(
                "ams",
                archive("AMSMST.COD", row("AMS", "SPY", "에스피디알 S&P 500", "3")).readByteArray()))
        .containsExactly(new KisStockName("AMS", "SPY", "에스피디알 S&P 500", "3"));
  }

  @Test
  void filtersUnsupportedSecuritiesAndCurrencyButKeepsMissingNamesForCounts() {
    zip(
        "NASMST.COD",
        String.join(
            "\n",
            row("NAS", "AAPL", " 애플 ", "2"),
            row("NAS", "NONAME", "", "2"),
            row("NAS", "ABC", "ENGLISH ONLY", "2"),
            row("NAS", "INDEX", "지수", "1"),
            row("NAS", "WARRANT", "워런트", "4"),
            row("NAS", "OTHER", "외화", "2").replace("USD", "EUR")));
    assertThat(parser.parse("nas", content))
        .extracting(KisStockName::koreanName)
        .containsExactly("애플", "", "ENGLISH ONLY");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-zip", "<html>maintenance</html>"})
  void rejectsNonArchiveResponses(String body) {
    content = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertInvalidMaster();
  }

  @ParameterizedTest
  @ValueSource(strings = {"../NASMST.COD", "OTHER.COD", "nested/NASMST.COD"})
  void rejectsUnexpectedArchivePaths(String filename) {
    zip(filename, row("NAS", "AAPL", "애플", "2"));
    assertInvalidMaster();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\n", "US\tNAS\tAAPL\t애플"})
  void rejectsEmptyOrChangedFormat(String body) {
    zip("NASMST.COD", body);
    assertInvalidMaster();
  }

  @Test
  void rejectsRowsFromAnotherMarket() {
    zip("NASMST.COD", row("NYS", "IBM", "아이비엠", "2"));
    assertInvalidMaster();
  }

  @Test
  void rejectsRowsFromAnotherCountry() {
    zip("NASMST.COD", row("NAS", "AAPL", "애플", "2").replaceFirst("US", "JP"));
    assertInvalidMaster();
  }

  @Test
  void rejectsDuplicateSecurities() {
    String row = row("NAS", "AAPL", "애플", "2");
    zip("NASMST.COD", row + "\n" + row);
    assertInvalidMaster();
  }

  @Test
  void rejectsInvalidCp949InsteadOfSavingReplacementCharacters() {
    content = archive("NASMST.COD", new byte[] {(byte) 0x81}).readByteArray();
    assertInvalidMaster();
  }

  @Test
  void rejectsOversizedUncompressedFile() {
    byte[] content = new byte[20 * 1024 * 1024 + 1];
    this.content = archive("NASMST.COD", content).readByteArray();
    assertInvalidMaster();
  }

  private void zip(String filename, String body) {
    content = archive(filename, body).readByteArray();
  }

  private void assertInvalidMaster() {
    assertThatThrownBy(() -> parser.parse("nas", content))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.errorCode())
                  .isEqualTo(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
              assertThat(exception.getCause()).isNull();
            })
        .isNotInstanceOf(org.grit.daynomy.external.RetryableExternalRequestException.class);
  }
}
