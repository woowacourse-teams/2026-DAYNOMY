package org.grit.daynomy.emailsubscription.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.content.domain.ContentSourceType;

/** 저장된 가격과 관련 자료만 담는다. version이 없는 과거 AI 메일은 조회만 허용한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailStockDigest(
    int version,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate baseDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate previousBaseDate,
    Map<Long, Long> holdings,
    List<Stock> stocks,
    List<Content> contents) {
  public EmailStockDigest {
    holdings = holdings == null ? Map.of() : Map.copyOf(holdings);
    stocks = stocks == null ? List.of() : List.copyOf(stocks);
    contents = contents == null ? List.of() : List.copyOf(contents);
    if (version == 1 && (stocks.size() > 3 || contents.size() > 3))
      throw new IllegalArgumentException("가격·관련 자료는 각각 최대 3개 안내할 수 있습니다.");
    if (version == 1) {
      for (var stock : stocks)
        if (!holdings.containsKey(stock.assetId()))
          throw new IllegalArgumentException("보유 종목만 안내할 수 있습니다.");
      for (var content : contents)
        if (!holdings.containsKey(content.assetId()))
          throw new IllegalArgumentException("보유 종목의 자료만 안내할 수 있습니다.");
    }
  }

  public static EmailStockDigest current(
      LocalDate baseDate,
      LocalDate previousBaseDate,
      Map<Long, Long> holdings,
      List<Stock> stocks,
      List<Content> contents) {
    return new EmailStockDigest(1, baseDate, previousBaseDate, holdings, stocks, contents);
  }

  public boolean currentNotification() {
    return version == 1;
  }

  public boolean hasNewInformation() {
    return !stocks.isEmpty() || !contents.isEmpty();
  }

  public String emailSections(String frontendBaseUrl) {
    var body = new StringBuilder();
    if (!stocks.isEmpty()) {
      body.append("보유 종목의 가격에 변화가 있어요\n").append(baseDate).append(" 종가 기준 · 현재 보유 수량 기준\n");
      for (var stock : stocks) {
        body.append(stock.name())
            .append(" — 종목 등락률: ")
            .append(number(stock.dailyReturnRate()))
            .append("%\n");
        if (stock.weight() != null && stock.contribution() != null)
          body.append(previousBaseDate)
              .append(" 종가 비중: ")
              .append(number(stock.weight()))
              .append("%\n내 자산의 가격 변동 기여도: ")
              .append(number(stock.contribution()))
              .append("%p\n");
      }
      body.append("\n");
    }
    if (!contents.isEmpty()) {
      body.append("보유 종목에 새 관련 자료가 등록됐어요\n");
      for (var content : contents)
        body.append(content.assetName())
            .append(" — ")
            .append(content.title())
            .append("\n")
            .append(content.platform())
            .append(" · 데이노미 등록일 ")
            .append(content.registeredAt().atZone(EmailDelivery.ZONE).toLocalDate())
            .append("\n")
            .append("자료 보기: ")
            .append(content.link(frontendBaseUrl))
            .append("\n\n");
    }
    return body.toString();
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Stock(
      Long assetId,
      String name,
      BigDecimal dailyReturnRate,
      BigDecimal weight,
      BigDecimal contribution) {}

  public record Content(
      Long id,
      String key,
      Long assetId,
      String assetName,
      ContentSourceType sourceType,
      String title,
      String url,
      Instant registeredAt) {
    public String platform() {
      return switch (sourceType) {
        case YOUTUBE -> "유튜브";
        case THREADS -> "Threads";
        case X -> "X";
        case INTERNAL_NEWS -> "데이노미 소식";
        case OTHER -> "관련 자료";
      };
    }

    public String link(String frontendBaseUrl) {
      return url.startsWith("/news/") ? frontendBaseUrl + url : url;
    }
  }
}
