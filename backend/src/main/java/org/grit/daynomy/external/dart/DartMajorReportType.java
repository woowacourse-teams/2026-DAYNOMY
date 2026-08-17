package org.grit.daynomy.external.dart;

import java.util.Arrays;
import java.util.List;

public enum DartMajorReportType {
  CAPITAL_INCREASE("유상증자"),
  CONVERTIBLE_BOND("전환사채"),
  MERGER("합병");

  private final String keyword;

  DartMajorReportType(String keyword) {
    this.keyword = keyword;
  }

  public static List<DartMajorReportType> fromAll(String reportName) {
    if (reportName == null) {
      return List.of();
    }

    return Arrays.stream(values()).filter(type -> reportName.contains(type.keyword)).toList();
  }
}
