package org.grit.daynomy.league.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.WeeklyLeaderboard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyLeaderboardRepository extends JpaRepository<WeeklyLeaderboard, Long> {
  Optional<WeeklyLeaderboard> findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
      LocalDate weekStart, LeagueType leagueType);
}
