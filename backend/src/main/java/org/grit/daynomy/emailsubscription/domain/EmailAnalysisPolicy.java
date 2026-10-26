package org.grit.daynomy.emailsubscription.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 금액은 USD. 실측한 모델 단가와 예약액을 설정하기 전에는 자동 실행하지 않는다. */
@ConfigurationProperties("email.analysis")
public record EmailAnalysisPolicy(
    boolean enabled,
    boolean costVerified,
    String model,
    BigDecimal dailyUsd,
    BigDecimal monthlyUsd,
    BigDecimal reserveUsd,
    BigDecimal inputUsdPerMillion,
    BigDecimal cachedUsdPerMillion,
    BigDecimal outputUsdPerMillion,
    BigDecimal searchUsdPerCall,
    int maxOutputTokens,
    int maxToolCalls) {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  public EmailAnalysisPolicy {
    if (enabled
        && (!costVerified
            || model == null
            || model.isBlank()
            || model.length() > 100
            || !positive(dailyUsd)
            || !positive(monthlyUsd)
            || !positive(reserveUsd)
            || reserveUsd.compareTo(dailyUsd) > 0
            || reserveUsd.compareTo(monthlyUsd) > 0
            || !positive(inputUsdPerMillion)
            || !positive(outputUsdPerMillion)
            || cachedUsdPerMillion == null
            || cachedUsdPerMillion.signum() < 0
            || cachedUsdPerMillion.compareTo(inputUsdPerMillion) > 0
            || !positive(searchUsdPerCall)
            || maxOutputTokens <= 0
            || maxToolCalls <= 0)) {
      throw new IllegalArgumentException("자동 분석에는 실측한 모델·비용 한도·단가·요청 제한이 필요합니다.");
    }
  }

  public boolean canStart(Instant now) {
    LocalTime time = now.atZone(ZONE).toLocalTime();
    return enabled && !time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(8, 45));
  }

  public BigDecimal cost(long input, long cached, long output, long searches) {
    if (input < 0 || cached < 0 || cached > input || output < 0 || searches < 0)
      throw new IllegalArgumentException("유효하지 않은 AI 사용량입니다.");
    return inputUsdPerMillion
        .multiply(BigDecimal.valueOf(input - cached))
        .add(cachedUsdPerMillion.multiply(BigDecimal.valueOf(cached)))
        .add(outputUsdPerMillion.multiply(BigDecimal.valueOf(output)))
        .divide(BigDecimal.valueOf(1_000_000))
        .add(searchUsdPerCall.multiply(BigDecimal.valueOf(searches)));
  }

  private static boolean positive(BigDecimal value) {
    return value != null
        && value.signum() > 0
        && value.scale() <= 10
        && value.precision() - value.scale() <= 10;
  }
}
