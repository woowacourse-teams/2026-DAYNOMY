package org.grit.daynomy.league.domain;

public final class LeagueTypes {
  private LeagueTypes() {}

  public enum ExperienceLevel {
    BEGINNER,
    ONE_TO_THREE_YEARS,
    OVER_THREE_YEARS
  }

  public enum RiskProfile {
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE
  }

  public enum TransactionType {
    BUY,
    SELL,
    HOLD
  }

  public enum HoldingPeriod {
    UNDER_ONE_MONTH,
    ONE_TO_THREE_MONTHS,
    THREE_TO_SIX_MONTHS,
    OVER_SIX_MONTHS
  }

  public enum LeagueType {
    WEEKLY_RETURN,
    CONSISTENT,
    STABLE,
    BEGINNER
  }
}
