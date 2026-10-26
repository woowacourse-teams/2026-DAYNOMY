package org.grit.daynomy.league.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyReturnValue {
  LocalDate getBaseDate();

  BigDecimal getDailyReturnRate();

  BigDecimal getMaxHoldingWeight();

  BigDecimal getCurrentHoldingWeight();

  boolean isEligible();

  boolean isTrackingBaseline();

  String getIneligibleReason();
}
