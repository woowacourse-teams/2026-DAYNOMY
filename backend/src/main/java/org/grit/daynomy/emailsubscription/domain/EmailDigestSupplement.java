package org.grit.daynomy.emailsubscription.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailDigestSupplement(Assets assets, Analysis analysis) {
  public EmailDigestSupplement(Assets assets) {
    this(assets, null);
  }

  public static EmailDigestSupplement empty() {
    return new EmailDigestSupplement(null);
  }

  public boolean shouldSend() {
    return analysis != null || (assets != null && assets.important());
  }

  public String body() {
    if (assets == null || assets.dailyProfitLoss().signum() == 0) return "";
    return "내 자산의 변화예요\n"
        + assets.baseDate()
        + " 종가 기준\n전체 평가금액: "
        + number(assets.evaluation())
        + "원\n해당일 보유 수량 기준 가격 변동 손익: "
        + number(assets.dailyProfitLoss())
        + "원 ("
        + number(assets.dailyReturnRate())
        + "%)\n\n";
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  public record Analysis(Long id, java.time.Instant analyzedAt, java.util.List<String> assetNames) {
    public Analysis {
      if (id == null || analyzedAt == null || assetNames == null || assetNames.isEmpty())
        throw new IllegalArgumentException("갱신된 분석과 종목이 필요합니다.");
      assetNames = java.util.List.copyOf(assetNames);
    }

    public String body() {
      return "보유 종목의 분석이 업데이트됐어요\n"
          + String.join(", ", assetNames)
          + "의 새 근거를 반영했어요.\n분석 기준: "
          + analyzedAt
              .atZone(EmailDelivery.ZONE)
              .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
          + " · 한국 시간\n사이트에서 저장된 분석을 확인해보세요.\n\n";
    }
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
}
