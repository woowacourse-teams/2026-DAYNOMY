package org.grit.daynomy.finance.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.finance.domain.FinancialPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialPlanRepository extends JpaRepository<FinancialPlan, Long> {
  List<FinancialPlan> findAllByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

  Optional<FinancialPlan> findByMemberIdAndPlanKey(Long memberId, String planKey);
}
