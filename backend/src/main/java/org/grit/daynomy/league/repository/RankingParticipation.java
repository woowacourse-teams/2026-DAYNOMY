package org.grit.daynomy.league.repository;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.league.domain.LeagueParticipationValue;

@Getter
@RequiredArgsConstructor
public class RankingParticipation implements LeagueParticipationValue {
  private final Instant startedAt;
  private final Instant endedAt;
}
