package org.grit.daynomy.league.repository;

public interface InvestmentDecisionCounts {
  Long getMemberId();

  long getDecisionCount();

  long getReviewedCount();
}
