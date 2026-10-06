package org.grit.daynomy.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.finance.domain.FinancialPlan;
import org.grit.daynomy.finance.dto.FinancialPlanDto.*;
import org.grit.daynomy.finance.exception.FinancialLearningErrorCode;
import org.grit.daynomy.finance.repository.FinancialPlanRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialPlanServiceTest {
  @Mock private FinancialPlanRepository repository;
  @Mock private MemberRepository members;
  @InjectMocks private FinancialPlanService service;

  @Test
  void unbalancedAllocationIsNotSaved() {
    var request =
        new PlanRequest(
            "저축·투자 계획",
            "월 계획",
            "요약",
            List.of(),
            List.of(),
            new MonthlyPlan(3000000, 1800000, 10000000, 1000000, 24, 420000, 600000, 200000, 0));
    assertThatThrownBy(() -> service.save(1L, "plan", request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(FinancialLearningErrorCode.INVALID_MONTHLY_ALLOCATION));
    then(repository).shouldHaveNoInteractions();
    then(members).shouldHaveNoInteractions();
  }

  @Test
  void negativeMoneyIsRejectedAtDomainBoundary() {
    var content =
        new FinancialPlan.Content(
            "저축·투자 계획",
            "계획",
            "요약",
            List.of(),
            List.of(),
            new FinancialPlan.Allocation(-1, 0, 0, 0, 24, 0, 0, 0, 0));
    assertThatThrownBy(() -> new FinancialPlan(mock(Member.class), "plan", content))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
