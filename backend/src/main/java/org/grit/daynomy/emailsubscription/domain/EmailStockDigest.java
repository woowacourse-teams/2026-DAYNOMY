package org.grit.daynomy.emailsubscription.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 전체 포트폴리오가 아닌, 선정된 보유 종목에 한정된 메일 내용. */
public record EmailStockDigest(LocalDate baseDate, LocalDate previousBaseDate, List<Stock> stocks) {
  public EmailStockDigest {
    stocks = List.copyOf(stocks);
  }

  public String subject() {
    return "[DAYNOMY] 오늘 주목할 보유 종목";
  }

  public String body() {
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
      body.append("종목 등락률: ").append(number(stock.dailyReturnRate())).append("%\n");
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
      CommonStockAnalysisContent content) {}
}
