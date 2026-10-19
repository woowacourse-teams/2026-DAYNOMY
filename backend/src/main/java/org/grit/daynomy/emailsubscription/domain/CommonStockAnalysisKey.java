package org.grit.daynomy.emailsubscription.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record CommonStockAnalysisKey(
    Long assetId, String model, String analysisVersion, String evidenceVersion, Instant asOf) {

  public CommonStockAnalysisKey {
    if (assetId == null || assetId <= 0) {
      throw new IllegalArgumentException("분석 대상 자산 ID가 필요합니다.");
    }
    requireVersion(model);
    requireVersion(analysisVersion);
    requireVersion(evidenceVersion);
    asOf = Objects.requireNonNull(asOf).truncatedTo(ChronoUnit.MICROS);
  }

  private static void requireVersion(String value) {
    if (value == null || value.isBlank() || value.length() > 100) {
      throw new IllegalArgumentException("모델과 분석·근거 버전은 1~100자로 입력해야 합니다.");
    }
  }
}
