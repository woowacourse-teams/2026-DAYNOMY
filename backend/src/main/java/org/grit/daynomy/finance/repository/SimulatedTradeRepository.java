package org.grit.daynomy.finance.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.finance.domain.SimulatedTrade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatedTradeRepository extends JpaRepository<SimulatedTrade, Long> {
  List<SimulatedTrade> findAllByMemberIdOrderByTradedOnDescIdDesc(Long memberId);

  Optional<SimulatedTrade> findByIdAndMemberId(Long id, Long memberId);
}
