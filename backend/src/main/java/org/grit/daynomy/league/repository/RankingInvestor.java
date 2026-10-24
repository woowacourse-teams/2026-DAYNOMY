package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.grit.daynomy.league.domain.LeagueInvestorValue;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;

@Getter
public class RankingInvestor implements LeagueInvestorValue {
  private final Long id;
  private final Long memberId;
  private final String publicId;
  private final String displayName;
  private final ExperienceLevel experienceLevel;
  private final RiskProfile riskProfile;
  private final LocalDate trackingStartedOn;
  private final String profileImageUrl;
  private final List<RankingParticipation> participations = new ArrayList<>();

  public RankingInvestor(
      Long id,
      Long memberId,
      String publicId,
      String displayName,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile,
      LocalDate trackingStartedOn,
      Instant startedAt,
      Instant endedAt) {
    this(
        id,
        memberId,
        publicId,
        displayName,
        experienceLevel,
        riskProfile,
        trackingStartedOn,
        startedAt,
        endedAt,
        null);
  }

  public RankingInvestor(
      Long id,
      Long memberId,
      String publicId,
      String displayName,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile,
      LocalDate trackingStartedOn,
      Instant startedAt,
      Instant endedAt,
      String profileImageUrl) {
    this.id = id;
    this.memberId = memberId;
    this.publicId = publicId;
    this.displayName = displayName;
    this.experienceLevel = experienceLevel;
    this.riskProfile = riskProfile;
    this.trackingStartedOn = trackingStartedOn;
    this.profileImageUrl = profileImageUrl;
    if (startedAt != null) participations.add(new RankingParticipation(startedAt, endedAt));
  }

  public Long memberId() {
    return memberId;
  }
}
