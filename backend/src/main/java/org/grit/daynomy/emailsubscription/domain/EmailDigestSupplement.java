package org.grit.daynomy.emailsubscription.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record EmailDigestSupplement(Assets assets, List<Event> events) {
  public EmailDigestSupplement {
    events = List.copyOf(events);
  }

  public static EmailDigestSupplement empty() {
    return new EmailDigestSupplement(null, List.of());
  }

  public boolean shouldSend() {
    return !events.isEmpty() || (assets != null && assets.important());
  }

  public boolean stillValid(EmailDigestSupplement current) {
    return (assets == null || assets.equals(current.assets()))
        && current.events().containsAll(events);
  }

  public String body() {
    var text = new StringBuilder();
    if (assets != null)
      text.append("\n기준일 보유 자산 요약 — ")
          .append(assets.baseDate())
          .append(" 종가\n평가 금액: ")
          .append(number(assets.evaluation()))
          .append("원\n일일 손익: ")
          .append(number(assets.dailyProfitLoss()))
          .append("원 (")
          .append(number(assets.dailyReturnRate()))
          .append("%)\n");
    if (!events.isEmpty()) text.append("\n미리 챙길 투자 일정\n");
    for (var event : events)
      text.append(event.title())
          .append(" — ")
          .append(
              event
                  .announcedAt()
                  .atZone(EmailAnalysisPolicy.ZONE)
                  .format(
                      DateTimeFormatter.ofPattern(
                          event.timeKnown() ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd")))
          .append(event.timeKnown() ? " 한국 시간\n출처: " : " (발표 시각 미확정)\n출처: ")
          .append(event.sourceUrl())
          .append("\n");
    return text.toString();
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  public record Assets(
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate baseDate,
      BigDecimal evaluation,
      BigDecimal dailyProfitLoss,
      BigDecimal dailyReturnRate) {
    public boolean important() {
      var previous = evaluation.subtract(dailyProfitLoss);
      return previous.signum() > 0
          && dailyProfitLoss
                  .abs()
                  .multiply(BigDecimal.valueOf(100))
                  .compareTo(previous.multiply(BigDecimal.valueOf(2)))
              >= 0;
    }
  }

  public record Event(
      String key, String title, Instant announcedAt, String sourceUrl, boolean timeKnown) {
    public Event(String key, String title, Instant announcedAt, String sourceUrl) {
      this(key, title, announcedAt, sourceUrl, true);
    }
  }
}
