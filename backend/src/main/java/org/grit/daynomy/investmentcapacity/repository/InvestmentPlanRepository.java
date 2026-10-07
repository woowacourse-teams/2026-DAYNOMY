package org.grit.daynomy.investmentcapacity.repository;

import java.util.Optional;
import org.grit.daynomy.investmentcapacity.domain.InvestmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentPlanRepository extends JpaRepository<InvestmentPlan, Long> {

  Optional<InvestmentPlan> findByMemberId(Long memberId);
}
