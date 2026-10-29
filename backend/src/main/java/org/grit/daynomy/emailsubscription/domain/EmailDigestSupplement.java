package org.grit.daynomy.emailsubscription.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailDigestSupplement(Assets assets) {
  public static EmailDigestSupplement empty() {
    return new EmailDigestSupplement(null);
  }

  public boolean shouldSend() {
    return assets != null && assets.important();
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
