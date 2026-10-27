package org.grit.daynomy.external.kis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okio.Buffer;

public final class KisStockMasterFixtures {
  private KisStockMasterFixtures() {}

  public static String row(String exchange, String symbol, String name, String type) {
    String[] fields = new String[24];
    Arrays.fill(fields, "");
    fields[0] = "US";
    fields[1] = "22";
    fields[2] = exchange;
    fields[3] = "거래소";
    fields[4] = symbol;
    fields[5] = exchange + symbol;
    fields[6] = name;
    fields[7] = "English name";
    fields[8] = type;
    fields[9] = "USD";
    return String.join("\t", fields);
  }

  public static Buffer archive(String filename, String content) {
    return archive(filename, content.getBytes(Charset.forName("MS949")));
  }

  public static Buffer archive(String filename, byte[] content) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
        zip.putNextEntry(new ZipEntry(filename));
        zip.write(content);
        zip.closeEntry();
      }
      return new Buffer().write(bytes.toByteArray());
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
