package org.grit.daynomy.league.domain;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;

public interface LeagueInvestorValue {
  Long getId();

  Long memberId();

  String getPublicId();

  String getDisplayName();

  ExperienceLevel getExperienceLevel();

  RiskProfile getRiskProfile();

  LocalDate getTrackingStartedOn();

  List<? extends LeagueParticipationValue> getParticipations();
}
