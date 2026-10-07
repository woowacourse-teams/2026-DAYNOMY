package org.grit.daynomy.investmentcapacity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.investmentcapacity.domain.InvestmentPlan;
import org.grit.daynomy.investmentcapacity.domain.PlanStatus;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanRequest;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanResponse;
import org.grit.daynomy.investmentcapacity.repository.InvestmentPlanRepository;
import org.grit.daynomy.external.finlife.FinlifeProduct;
import org.grit.daynomy.external.finlife.FinlifeProductClient;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentPlanServiceTest {

  @Mock private InvestmentPlanRepository investmentPlanRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private FinlifeProductClient finlifeProductClient;
  @InjectMocks private InvestmentPlanService service;

  @Test
  void savesPlanAndCalculatesSafeCapacityAndGoalNeed() {
    Member member = mock(Member.class);
    given(memberRepository.findById(3L)).willReturn(Optional.of(member));
    given(investmentPlanRepository.findByMemberId(3L)).willReturn(Optional.empty());
    given(investmentPlanRepository.save(any(InvestmentPlan.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(finlifeProductClient.getProducts()).willReturn(products());

    InvestmentPlanResponse response = service.save(3L, request(10_000_000, 24));

    assertThat(response.safeMonthlyCapacity()).isEqualTo(2_000_000);
    assertThat(response.emergencyFundTarget()).isEqualTo(6_000_000);
    assertThat(response.emergencyFundGap()).isEqualTo(3_000_000);
    assertThat(response.requiredMonthlySaving()).isEqualTo(416_667);
    assertThat(response.status()).isEqualTo(PlanStatus.ADJUSTABLE);
    assertThat(response.scenarios()).hasSize(2);
    assertThat(response.products()).isNotEmpty();
    assertThat(response.products().get(0).baseRate()).hasToString("3.5");
    assertThat(response.products().get(0).maxRate()).hasToString("4.5");
    assertThat(response.products().get(0).benefitConditions()).extracting("status").containsOnly("확인 필요");
    assertThat(response.products().get(0).amountConditions().get(0).thresholdAmount())
        .isEqualTo(50_000_000L);
  }

  @Test
  void updatesExistingPlanInsteadOfCreatingAnotherPlan() {
    InvestmentPlan existing = InvestmentPlan.create(mock(Member.class), request(5_000_000, 12));
    given(investmentPlanRepository.findByMemberId(3L)).willReturn(Optional.of(existing));
    InvestmentPlanRequest request = request(8_000_000, 18);
    given(finlifeProductClient.getProducts()).willReturn(products());

    InvestmentPlanResponse response = service.save(3L, request);

    assertThat(response.goalAmount()).isEqualTo(8_000_000);
    assertThat(response.goalMonths()).isEqualTo(18);
    then(investmentPlanRepository).should(never()).save(any(InvestmentPlan.class));
    then(memberRepository).shouldHaveNoInteractions();
  }

  @Test
  void showsDepositsAndSavingsSeparatelyInsteadOfUsingUpstreamOrder() {
    Member member = mock(Member.class);
    given(memberRepository.findById(3L)).willReturn(Optional.of(member));
    given(investmentPlanRepository.findByMemberId(3L)).willReturn(Optional.empty());
    given(investmentPlanRepository.save(any(InvestmentPlan.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(finlifeProductClient.getProducts())
        .willReturn(java.util.stream.Stream.concat(java.util.stream.Stream.of(products().get(0)), java.util.stream.Stream.of(depositProduct())).toList());

    InvestmentPlanResponse response = service.save(3L, request(10_000_000, 24));

    assertThat(response.products()).extracting("type").containsExactly("예금", "적금");
  }

  @Test
  void recommendsAvailableAmountsWhenGoalIsNotSet() {
    Member member = mock(Member.class);
    given(memberRepository.findById(3L)).willReturn(Optional.of(member));
    given(investmentPlanRepository.findByMemberId(3L)).willReturn(Optional.empty());
    given(investmentPlanRepository.save(any(InvestmentPlan.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(finlifeProductClient.getProducts()).willReturn(products());

    InvestmentPlanResponse response = service.save(3L, request(0, 12));

    assertThat(response.status()).isEqualTo(PlanStatus.GOAL_NOT_SET);
    assertThat(response.requiredMonthlySaving()).isZero();
    assertThat(response.scenarios().get(0).monthlySaving()).isPositive();
    assertThat(response.products()).anySatisfy(product -> assertThat(product.monthlyDeposit()).isPositive());
  }

  @Test
  void keepsMonthlyCapacitySeparateFromExistingDepositSavings() {
    Member member = mock(Member.class);
    given(memberRepository.findById(3L)).willReturn(Optional.of(member));
    given(investmentPlanRepository.findByMemberId(3L)).willReturn(Optional.empty());
    given(investmentPlanRepository.save(any(InvestmentPlan.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(finlifeProductClient.getProducts()).willReturn(products());

    InvestmentPlanResponse response = service.save(3L, request(0, 12, 5_000_000));

    assertThat(response.safeMonthlyCapacity()).isEqualTo(2_000_000);
    assertThat(response.existingDepositSavings()).isEqualTo(5_000_000);
    assertThat(response.availableCurrentCash()).isEqualTo(2_000_000);
    assertThat(response.totalAssets()).isEqualTo(8_000_000);
  }

  @Test
  void returnsNullWhenMemberHasNoSavedPlan() {
    given(investmentPlanRepository.findByMemberId(3L)).willReturn(Optional.empty());

    assertThat(service.get(3L)).isNull();
  }

  private InvestmentPlanRequest request(long goalAmount, int goalMonths) {
    return request(goalAmount, goalMonths, 0);
  }

  private InvestmentPlanRequest request(
      long goalAmount, int goalMonths, long existingDepositSavings) {
    return new InvestmentPlanRequest(
        LocalDate.of(2000, 1, 1),
        3_000_000,
        1_000_000,
        0,
        0,
        0,
        0,
        3_000_000,
        existingDepositSavings,
        0,
        0,
        goalAmount,
        goalMonths,
        "STABLE",
        null);
  }

  private java.util.List<FinlifeProduct> products() {
    return java.util.List.of(
        new FinlifeProduct(
            "0010001-TEST-적금-12",
            "테스트은행",
            "테스트 적금",
            "적금",
            new java.math.BigDecimal("3.5"),
            new java.math.BigDecimal("4.5"),
            12,
            1_000_000,
            "인터넷뱅킹",
            "급여이체\n1. 5천만원 이상 가입 시 0.1%p",
            "실명의 개인",
            "1",
        "202610"));
  }

  private FinlifeProduct depositProduct() {
    return new FinlifeProduct(
        "0010002-TEST-예금-12",
        "테스트은행",
        "테스트 예금",
        "예금",
        new java.math.BigDecimal("4.0"),
        new java.math.BigDecimal("4.2"),
        12,
        10_000_000,
        "인터넷뱅킹",
        "1. 1천만원 이상 가입 시 0.2%p",
        "실명의 개인",
        "1",
        "202610");
  }
}
