package org.grit.daynomy.emailsubscription.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** 서버의 원문 검증 실행기가 기록할 결과다. AI가 반환한 자체 평가를 전달해서는 안 된다. */
public record CommonStockAnalysisVerification(
    String analysisContentHash, Instant checkedAt, List<SourceCheck> sources) {

  public CommonStockAnalysisVerification {
    if (analysisContentHash == null || !analysisContentHash.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("검증 대상 분석 내용의 해시가 필요합니다.");
    }
    checkedAt = Objects.requireNonNull(checkedAt).truncatedTo(ChronoUnit.MICROS);
    sources = List.copyOf(sources);
  }

  public record SourceCheck(
      String url,
      String originalContentHash,
      boolean companyMatched,
      boolean datesMatched,
      boolean numbersMatched,
      boolean quoteMatched) {

    public boolean passed() {
      return originalContentHash != null
          && originalContentHash.matches("[a-f0-9]{64}")
          && companyMatched
          && datesMatched
          && numbersMatched
          && quoteMatched;
    }
  }
}
