package org.grit.daynomy.external.kis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.kis.dto.KisStockName;
import org.springframework.stereotype.Component;

@Component
public class KisStockMasterParser {
  private static final Charset CP949 = Charset.forName("MS949");
  private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9./-]{0,39}");
  private static final int COLUMN_COUNT = 24;
  private static final long MAX_MASTER_BYTES = 20 * 1024 * 1024;

  public List<KisStockName> parse(String market, byte[] archive) {
    if (market == null || !KisStockMasterClient.US_MARKETS.contains(market)) {
      throw new IllegalArgumentException("지원하지 않는 미국 종목정보 시장입니다.");
    }
    try {
      return readArchive(archive, market);
    } catch (IOException exception) {
      throw invalidMaster();
    }
  }

  private List<KisStockName> readArchive(byte[] archive, String market) throws IOException {
    // Read only the expected entry in memory; never extract archive paths to the filesystem.
    List<KisStockName> result = null;
    try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().equalsIgnoreCase(market + "mst.cod")) {
          throw invalidMaster();
        }
        if (result != null || entry.getSize() > MAX_MASTER_BYTES) throw invalidMaster();
        byte[] content = zip.readNBytes(Math.toIntExact(MAX_MASTER_BYTES + 1));
        if (content.length > MAX_MASTER_BYTES) throw invalidMaster();
        result = parse(content, market);
      }
    }
    if (result == null || result.isEmpty()) throw invalidMaster();
    return result;
  }

  private List<KisStockName> parse(byte[] content, String market) throws IOException {
    var decoder =
        CP949
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    List<KisStockName> result = new ArrayList<>();
    Set<String> symbols = new HashSet<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new java.io.ByteArrayInputStream(content), decoder))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;
        String[] fields = line.split("\t", -1);
        if (fields.length != COLUMN_COUNT) throw invalidMaster();
        for (int i = 0; i < fields.length; i++) fields[i] = fields[i].strip();
        if (!"US".equals(fields[0])
            || !market.toUpperCase(Locale.ROOT).equals(fields[2])
            || !SYMBOL.matcher(fields[4]).matches()
            || !Set.of("1", "2", "3", "4").contains(fields[8])
            || fields[6].length() > 255) {
          throw invalidMaster();
        }
        if (!Set.of("2", "3").contains(fields[8]) || !"USD".equals(fields[9])) continue;
        if (!symbols.add(fields[8] + ":" + fields[4])) throw invalidMaster();
        result.add(new KisStockName(fields[2], fields[4], fields[6], fields[8]));
      }
    }
    return List.copyOf(result);
  }

  private BusinessException invalidMaster() {
    return new BusinessException(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
  }
}
