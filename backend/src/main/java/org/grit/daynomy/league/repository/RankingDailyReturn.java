package org.grit.daynomy.league.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.league.domain.DailyReturnValue;

@Getter
@RequiredArgsConstructor
public class RankingDailyReturn implements DailyReturnValue {
  private final Long memberId;
  private final LocalDate baseDate;
  private final BigDecimal dailyReturnRate;
  private final BigDecimal maxHoldingWeight;
  private final BigDecimal currentHoldingWeight;
  private final boolean eligible;
  private final boolean trackingBaseline;
}
