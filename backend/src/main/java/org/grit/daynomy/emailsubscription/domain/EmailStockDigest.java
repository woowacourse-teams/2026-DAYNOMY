package org.grit.daynomy.emailsubscription.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;

/** 전체 포트폴리오가 아닌, 선정된 보유 종목에 한정된 메일 내용. */
public record EmailStockDigest(LocalDate baseDate, LocalDate previousBaseDate, List<Stock> stocks) {
  public EmailStockDigest {
    stocks = List.copyOf(stocks);
  }

  public String subject() {
    return "[DAYNOMY] 오늘 주목할 보유 종목";
  }

  public String body() {
    return body(true);
  }

  public String bodyWithoutChanges() {
    return body(false);
  }

  /** 문장·모델·분석 ID 변경은 새 정보로 보지 않는다. 비교 기준은 회원이 마지막으로 안내받은 종목이다. */
  public EmailStockDigest newInformation(Map<Long, EmailStockDigest> previous, Set<Long> blocked) {
    var fresh = new java.util.ArrayList<Stock>();
    for (var stock : stocks) {
      if (blocked.contains(stock.assetId())) continue;
      var digest = previous.get(stock.assetId());
      var before =
          digest == null
              ? null
              : digest.stocks().stream()
                  .filter(item -> item.assetId().equals(stock.assetId()))
                  .findFirst()
                  .orElse(null);
      String change;
      if (before == null) change = "처음 안내하는 종목 분석입니다.";
      else {
        var evidence = evidence(stock.content());
        evidence.removeAll(evidence(before.content()));
        boolean newEvidence = !evidence.isEmpty();
        boolean priceChanged =
            baseDate.isAfter(digest.baseDate())
                && ((stock.dailyReturnRate() != null
                        && stock.dailyReturnRate().abs().compareTo(BigDecimal.valueOf(5)) >= 0)
                    || (stock.contribution() != null
                        && stock.contribution().abs().compareTo(BigDecimal.ONE) >= 0));
        if (!newEvidence && !priceChanged) continue;
        boolean analysisChanged =
            newEvidence
                && stock.content().direction() != null
                && before.content().direction() != null
                && stock.content().impactLevel() != null
                && before.content().impactLevel() != null
                && (stock.content().direction() != before.content().direction()
                    || stock.content().impactLevel() != before.content().impactLevel());
        change =
            analysisChanged
                ? "새 근거에 따른 분석 변화: "
                    + analysisLabel(before.content())
                    + " → "
                    + analysisLabel(stock.content())
                : newEvidence ? "이전 안내 이후 새로운 원문 근거가 확인됐습니다." : "새 가격 기준일의 중요한 변동입니다.";
      }
      fresh.add(
          new Stock(
              stock.analysisId(),
              stock.assetId(),
              stock.name(),
              stock.dailyReturnRate(),
              stock.weight(),
              stock.contribution(),
              stock.content(),
              change));
    }
    return new EmailStockDigest(baseDate, previousBaseDate, fresh);
  }

  private static Set<String> evidence(CommonStockAnalysisContent content) {
    var result = new HashSet<String>();
    for (Source source : content.sources()) {
      if (source.factQuote() == null || source.factQuote().isBlank()) continue;
      // 같은 원문 인용은 제목·URL·AI 설명이 바뀌어도 같은 근거다. 동일 문구의 다른 사건은 발생일로 구분한다.
      result.add(
          Objects.toString(source.occurredOn(), "")
              + "|"
              + source.factQuote().replaceAll("\\s+", ""));
    }
    return result;
  }

  private static String analysisLabel(CommonStockAnalysisContent content) {
    String direction =
        switch (content.direction()) {
          case POSITIVE -> "긍정";
          case NEGATIVE -> "부정";
          case NEUTRAL -> "중립";
        };
    String level =
        switch (content.impactLevel()) {
          case HIGH -> "높음";
          case MEDIUM -> "보통";
          case LOW -> "낮음";
        };
    return direction + "·영향 " + level;
  }

  private String body(boolean includeChanges) {
    StringBuilder body = new StringBuilder("오늘 주목할 보유 종목\n");
    body.append("중요 종목만 선별한 부분 분석입니다.\n");
    body.append("가격 기준일: ").append(baseDate).append(" 종가\n");
    for (Stock stock : stocks) {
      body.append("\n")
          .append(stock.name())
          .append(" — ")
          .append(stock.content().issueSummary())
          .append("\n");
      body.append(stock.content().reason()).append("\n");
      if (includeChanges && stock.changeSummary() != null)
        body.append(stock.changeSummary()).append("\n");
      if (stock.dailyReturnRate() != null)
        body.append("종목 등락률: ").append(number(stock.dailyReturnRate())).append("%\n");
      else body.append("종목 가격 정보 미확인\n");
      if (stock.weight() != null && stock.contribution() != null) {
        body.append("현재 보유 수량 기준, ")
            .append(previousBaseDate)
            .append(" 종가 비중: ")
            .append(number(stock.weight()))
            .append("%\n");
        body.append("내 자산의 가격 변동 기여도: ").append(number(stock.contribution())).append("%p\n");
      }
      stock
          .content()
          .sources()
          .forEach(source -> body.append("출처: ").append(source.url()).append("\n"));
    }
    return body.toString();
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  public record Stock(
      Long analysisId,
      Long assetId,
      String name,
      BigDecimal dailyReturnRate,
      BigDecimal weight,
      BigDecimal contribution,
      CommonStockAnalysisContent content,
      String changeSummary) {
    public Stock(
        Long analysisId,
        Long assetId,
        String name,
        BigDecimal dailyReturnRate,
        BigDecimal weight,
        BigDecimal contribution,
        CommonStockAnalysisContent content) {
      this(analysisId, assetId, name, dailyReturnRate, weight, contribution, content, null);
    }
  }
}
