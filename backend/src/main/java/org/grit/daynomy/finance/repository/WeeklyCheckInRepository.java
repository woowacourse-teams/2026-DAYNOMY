package org.grit.daynomy.finance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.finance.domain.WeeklyCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCheckInRepository extends JpaRepository<WeeklyCheckIn, Long> {
  Optional<WeeklyCheckIn> findByMemberIdAndWeekStart(Long memberId, LocalDate weekStart);

  List<WeeklyCheckIn> findAllByMemberIdOrderByWeekStartDesc(Long memberId);
}
