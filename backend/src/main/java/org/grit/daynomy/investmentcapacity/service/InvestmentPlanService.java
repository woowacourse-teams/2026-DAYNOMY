package org.grit.daynomy.investmentcapacity.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.finlife.FinlifeProduct;
import org.grit.daynomy.external.finlife.FinlifeProductClient;
import org.grit.daynomy.investmentcapacity.domain.InvestmentPlan;
import org.grit.daynomy.investmentcapacity.domain.PlanStatus;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanRequest;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanResponse;
import org.grit.daynomy.investmentcapacity.dto.InvestmentBenefitConditionResponse;
import org.grit.daynomy.investmentcapacity.dto.InvestmentAmountConditionResponse;
import org.grit.daynomy.investmentcapacity.dto.InvestmentProductResponse;
import org.grit.daynomy.investmentcapacity.dto.InvestmentProductTermResponse;
import org.grit.daynomy.investmentcapacity.dto.InvestmentScenarioResponse;
import org.grit.daynomy.investmentcapacity.repository.InvestmentPlanRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentPlanService {

  private static final long EMERGENCY_FUND_MONTHS = 6;
  private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(1200);
  private static final Pattern AMOUNT_PATTERN =
      Pattern.compile("(\\d+(?:[.]\\d+)?)\\s*(억|천만|백만|만)\\s*원?");

  private final InvestmentPlanRepository investmentPlanRepository;
  private final MemberRepository memberRepository;
  private final FinlifeProductClient finlifeProductClient;

  @Transactional(readOnly = true)
  public InvestmentPlanResponse get(Long memberId) {
    return investmentPlanRepository.findByMemberId(memberId).map(this::toResponse).orElse(null);
  }

  @Transactional
  public InvestmentPlanResponse save(Long memberId, InvestmentPlanRequest request) {
    InvestmentPlan plan =
        investmentPlanRepository
            .findByMemberId(memberId)
            .map(
                savedPlan -> {
                  savedPlan.update(request);
                  return savedPlan;
                })
            .orElseGet(
                () -> {
                  Member member =
                      memberRepository
                          .findById(memberId)
                          .orElseThrow(
                              () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
                  return investmentPlanRepository.save(InvestmentPlan.create(member, request));
                });
    return toResponse(plan);
  }

  private InvestmentPlanResponse toResponse(InvestmentPlan plan) {
    long monthlyEssentials =
        safeAdd(plan.getMonthlyFixedExpense(), plan.getMonthlyVariableExpense());
    monthlyEssentials = safeAdd(monthlyEssentials, plan.getIrregularExpenseReserve());
    long totalMonthlyOutflow =
        safeAdd(monthlyEssentials, plan.getEmergencyFundContribution());
    totalMonthlyOutflow = safeAdd(totalMonthlyOutflow, plan.getMonthlyDebtRepayment());
    long emergencyFundTarget =
        multiplySafely(
            safeAdd(plan.getMonthlyFixedExpense(), plan.getMonthlyVariableExpense()),
            EMERGENCY_FUND_MONTHS);
    long emergencyFundGap = Math.max(0, emergencyFundTarget - plan.getCurrentCash());
    long safeMonthlyCapacity = Math.max(0, plan.getMonthlyIncome() - totalMonthlyOutflow);
    long availableAssetCash = safeAdd(plan.getCurrentCash(), plan.getExistingDepositSavings());
    long availableCurrentCash = Math.max(0, availableAssetCash - emergencyFundTarget);
    long totalAssets = safeAdd(availableAssetCash, plan.getInvestmentAssets());
    totalAssets = safeAdd(totalAssets, plan.getOtherAssets());
    long requiredMonthlySaving =
        plan.getGoalAmount() == 0
            ? 0
            : divideCeiling(Math.max(0, plan.getGoalAmount() - availableCurrentCash), plan.getGoalMonths());
    PlanStatus status = determineStatus(plan.getGoalAmount(), emergencyFundGap, requiredMonthlySaving, safeMonthlyCapacity);

    List<InvestmentScenarioResponse> scenarios =
        List.of(
            createStableScenario(
                plan, emergencyFundGap, safeMonthlyCapacity, availableCurrentCash, requiredMonthlySaving),
            createBalancedScenario(
                plan, emergencyFundGap, safeMonthlyCapacity, availableCurrentCash, requiredMonthlySaving));

    List<InvestmentProductResponse> products =
        createProducts(plan, safeMonthlyCapacity, availableCurrentCash, requiredMonthlySaving);

    return new InvestmentPlanResponse(
        plan.getBirthDate(),
        plan.getMonthlyIncome(),
        plan.getMonthlyFixedExpense(),
        plan.getMonthlyVariableExpense(),
        plan.getIrregularExpenseReserve(),
        plan.getEmergencyFundContribution(),
        plan.getMonthlyDebtRepayment(),
        plan.getCurrentCash(),
        plan.getExistingDepositSavings(),
        plan.getInvestmentAssets(),
        plan.getOtherAssets(),
        totalAssets,
        availableCurrentCash,
        plan.getGoalAmount(),
        plan.getGoalMonths(),
        emergencyFundTarget,
        emergencyFundGap,
        safeMonthlyCapacity,
        requiredMonthlySaving,
        status,
        createInterpretation(plan, status, emergencyFundGap, requiredMonthlySaving, safeMonthlyCapacity),
        plan.getSelectedScenario(),
        plan.getSelectedProductId(),
        scenarios,
        products);
  }

  private InvestmentScenarioResponse createStableScenario(
      InvestmentPlan plan,
      long emergencyFundGap,
      long safeMonthlyCapacity,
      long availableCurrentCash,
      long requiredMonthlySaving) {
    long monthlyEmergencyFund = Math.min(emergencyFundGap, safeMonthlyCapacity / 5);
    long availableForSaving = Math.max(0, safeMonthlyCapacity - monthlyEmergencyFund);
    long monthlySaving =
        plan.getGoalAmount() == 0
            ? availableForSaving
            : Math.min(requiredMonthlySaving, availableForSaving);
    long expectedAmount =
        expectedAmount(availableCurrentCash, monthlySaving, plan.getGoalMonths(), BigDecimal.valueOf(3.0));
    PlanStatus status =
        plan.getGoalAmount() == 0
            ? PlanStatus.GOAL_NOT_SET
            : (monthlySaving >= requiredMonthlySaving
                ? PlanStatus.ACHIEVABLE
                : PlanStatus.DIFFICULT);
    return new InvestmentScenarioResponse(
        "STABLE",
        "안정형",
        "목표 자금은 예금·적금 중심으로 모아 원금 변동 가능성을 낮춰요.",
        monthlySaving,
        0,
        monthlyEmergencyFund,
        expectedAmount,
        status,
        "중도 해지하면 약정 금리와 만기 혜택을 받지 못할 수 있어요.");
  }

  private InvestmentScenarioResponse createBalancedScenario(
      InvestmentPlan plan,
      long emergencyFundGap,
      long safeMonthlyCapacity,
      long availableCurrentCash,
      long requiredMonthlySaving) {
    long monthlyEmergencyFund = Math.min(emergencyFundGap, safeMonthlyCapacity / 5);
    long flexibleAmount = Math.max(0, safeMonthlyCapacity - monthlyEmergencyFund);
    long monthlySaving =
        plan.getGoalAmount() == 0
            ? flexibleAmount * 80 / 100
            : Math.min(requiredMonthlySaving, flexibleAmount * 80 / 100);
    long monthlyInvesting = Math.max(0, flexibleAmount - monthlySaving);
    long expectedAmount =
        expectedAmount(availableCurrentCash, monthlySaving, plan.getGoalMonths(), BigDecimal.valueOf(3.0));
    PlanStatus status =
        plan.getGoalAmount() == 0
            ? PlanStatus.GOAL_NOT_SET
            : (monthlySaving >= requiredMonthlySaving
                ? PlanStatus.ACHIEVABLE
                : PlanStatus.DIFFICULT);
    return new InvestmentScenarioResponse(
        "BALANCED",
        "균형형",
        "목표 자금은 안전하게 확보하고, 장기 여유자금만 작은 비중으로 투자해요.",
        monthlySaving,
        monthlyInvesting,
        monthlyEmergencyFund,
        expectedAmount,
        status,
        "투자금은 목표 기간 전에 사용할 가능성이 있다면 줄이거나 제외해야 해요.");
  }

  private List<InvestmentProductResponse> createProducts(
      InvestmentPlan plan, long safeMonthlyCapacity, long availableCurrentCash, long requiredMonthlySaving) {
    long targetAmount =
        plan.getGoalAmount() == 0
            ? safeMonthlyCapacity
            : Math.max(0, Math.min(safeMonthlyCapacity, requiredMonthlySaving));
    java.util.Map<String, List<FinlifeProduct>> productGroups = new java.util.LinkedHashMap<>();
    finlifeProductClient.getProducts().stream()
        .filter(product -> product.baseRate() != null && product.termMonths() > 0)
        .forEach(
            product ->
                productGroups
                    .computeIfAbsent(productGroupKey(product), ignored -> new java.util.ArrayList<>())
                    .add(product));
    Comparator<List<FinlifeProduct>> productComparator =
        Comparator.comparingInt(
                (List<FinlifeProduct> group) -> closestTermDistance(group, plan.getGoalMonths()))
            .thenComparingInt(
                group -> amountFitPenalty(group, plan, targetAmount, availableCurrentCash))
            .thenComparing(
                group -> selectVariant(group, plan.getGoalMonths()).baseRate(),
                Comparator.reverseOrder())
            .thenComparing(
                group -> selectVariant(group, plan.getGoalMonths()).maxRate(),
                Comparator.reverseOrder())
            .thenComparing(
                group -> selectVariant(group, plan.getGoalMonths()).disclosureMonth(),
                Comparator.reverseOrder());
    List<List<FinlifeProduct>> sortedDeposits =
        productGroups.values().stream()
            .filter(group -> "예금".equals(group.get(0).type()))
            .sorted(productComparator)
            .limit(3)
            .toList();
    List<List<FinlifeProduct>> sortedSavings =
        productGroups.values().stream()
            .filter(group -> "적금".equals(group.get(0).type()))
            .sorted(productComparator)
            .limit(3)
            .toList();
    return Stream.concat(sortedDeposits.stream(), sortedSavings.stream())
        .map(
            variants -> {
              FinlifeProduct selectedVariant = selectVariant(variants, plan.getGoalMonths());
              return toProductResponse(
                  plan, selectedVariant, targetAmount, availableCurrentCash, variants);
            })
        .toList();
  }

  private int amountFitPenalty(
      List<FinlifeProduct> variants,
      InvestmentPlan plan,
      long targetAmount,
      long availableCurrentCash) {
    FinlifeProduct product = selectVariant(variants, plan.getGoalMonths());
    long requiredAmount =
        "적금".equals(product.type())
            ? targetAmount
            : plan.getGoalAmount() == 0
                ? availableCurrentCash
                : Math.max(targetAmount, availableCurrentCash);
    return product.maxLimit() > 0 && requiredAmount > product.maxLimit() ? 1 : 0;
  }

  private InvestmentProductResponse toProductResponse(
      InvestmentPlan plan,
      FinlifeProduct product,
      long targetAmount,
      long availableCurrentCash,
      List<FinlifeProduct> variants) {
    int calculationMonths = Math.min(plan.getGoalMonths(), product.termMonths());
    boolean savings = "적금".equals(product.type());
    long recommendedAmount =
        recommendedAmount(
            product, targetAmount, availableCurrentCash, savings, plan.getGoalAmount() > 0);
    if (product.maxLimit() > 0) {
      recommendedAmount = Math.min(recommendedAmount, product.maxLimit());
    }

    long maturity =
        savings
            ? expectedAmount(0, recommendedAmount, calculationMonths, product.baseRate())
            : expectedAmount(recommendedAmount, 0, calculationMonths, product.baseRate());
    long principal = savings ? recommendedAmount * calculationMonths : recommendedAmount;
    return new InvestmentProductResponse(
        product.id(),
        product.productName(),
        product.type(),
        product.baseRate(),
        product.maxRate(),
        product.maxLimit(),
        product.termMonths(),
        recommendedAmount,
        maturity,
        Math.max(0, maturity - principal),
        savings ? "낮음" : "높음",
        savings
            ? "중도 해지 시 약정 금리와 만기 혜택을 받지 못할 수 있어요."
            : "출금 시점에 따라 적용 이율과 만기 혜택이 달라질 수 있어요.",
        true,
        joinEligibility(product),
        recommendationReason(product, plan, targetAmount),
        "금융감독원 금융상품 한눈에 API의 "
            + product.disclosureMonth()
            + " 공시 기준입니다. 실제 가입 전 금융기관 상품설명서를 확인하세요.",
        product.companyName(),
        product.joinWay(),
        splitBenefitConditions(product.specialConditions()),
        splitAmountConditions(product.specialConditions()),
        product.disclosureMonth(),
        "https://finlife.fss.or.kr/finlife/main/main.do?menuNo=700000",
        variants.stream()
            .sorted(Comparator.comparingInt(FinlifeProduct::termMonths))
            .map(variant -> toTermResponse(plan, variant, targetAmount, availableCurrentCash))
            .toList());
  }

  private InvestmentProductTermResponse toTermResponse(
      InvestmentPlan plan, FinlifeProduct product, long targetAmount, long availableCurrentCash) {
    boolean savings = "적금".equals(product.type());
    long recommendedAmount =
        recommendedAmount(
            product, targetAmount, availableCurrentCash, savings, plan.getGoalAmount() > 0);
    int calculationMonths = Math.min(plan.getGoalMonths(), product.termMonths());
    long maturity =
        savings
            ? expectedAmount(0, recommendedAmount, calculationMonths, product.baseRate())
            : expectedAmount(recommendedAmount, 0, calculationMonths, product.baseRate());
    long principal = savings ? recommendedAmount * calculationMonths : recommendedAmount;
    return new InvestmentProductTermResponse(
        product.id(),
        product.termMonths(),
        product.baseRate(),
        product.maxRate(),
        recommendedAmount,
        maturity,
        Math.max(0, maturity - principal));
  }

  private long recommendedAmount(
      FinlifeProduct product,
      long targetAmount,
      long availableCurrentCash,
      boolean savings,
      boolean goalSet) {
    long recommendedAmount =
        savings ? targetAmount : (goalSet ? Math.max(targetAmount, availableCurrentCash) : availableCurrentCash);
    if (product.maxLimit() > 0) {
      recommendedAmount = Math.min(recommendedAmount, product.maxLimit());
    }
    return recommendedAmount;
  }

  private String productGroupKey(FinlifeProduct product) {
    return product.companyName() + "|" + product.productName() + "|" + product.type();
  }

  private int closestTermDistance(List<FinlifeProduct> variants, int goalMonths) {
    return variants.stream()
        .mapToInt(product -> Math.abs(product.termMonths() - goalMonths))
        .min()
        .orElse(Integer.MAX_VALUE);
  }

  private FinlifeProduct selectVariant(List<FinlifeProduct> variants, int goalMonths) {
    return variants.stream()
        .min(
            Comparator.comparingInt(
                    (FinlifeProduct product) -> Math.abs(product.termMonths() - goalMonths))
                .thenComparing(FinlifeProduct::baseRate, Comparator.reverseOrder()))
        .orElseThrow();
  }

  private List<InvestmentBenefitConditionResponse> splitBenefitConditions(String conditions) {
    if (conditions == null || conditions.isBlank()) {
      return List.of();
    }
    String normalized = conditions.trim();
    if ("-".equals(normalized)
        || "없음".equals(normalized)
        || "해당없음".equals(normalized)
        || "우대조건 없음".equals(normalized)) {
      return List.of();
    }
    return Arrays.stream(conditions.replace('\n', ' ').split("(?=\\d+\\.(?!\\d))|(?=[①-⑩])"))
        .map(String::trim)
        .filter(condition -> !condition.isBlank())
        .map(condition -> new InvestmentBenefitConditionResponse(condition, "확인 필요"))
        .toList();
  }

  private List<InvestmentAmountConditionResponse> splitAmountConditions(String conditions) {
    return splitConditionTexts(conditions).stream()
        .map(
            condition -> {
              Long thresholdAmount = findProductAmountThreshold(condition);
              if (thresholdAmount == null) return null;
              return new InvestmentAmountConditionResponse(
                  condition, thresholdAmount, "확인 필요");
            })
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private Long findProductAmountThreshold(String condition) {
    Matcher matcher = AMOUNT_PATTERN.matcher(condition);
    while (matcher.find()) {
      int end = matcher.end();
      int contextStart = Math.max(0, matcher.start() - 24);
      int contextEnd = Math.min(condition.length(), end + 12);
      String before = condition.substring(contextStart, matcher.start()).replace(" ", "");
      String after = condition.substring(end, contextEnd).replace(" ", "");
      if (before.matches(".*(가입금액|예치금액|납입금액)$")
          || after.matches(".*이상(가입|예치|납입).*")) {
        return parseKoreanAmount(matcher.group(1), matcher.group(2));
      }
    }
    return null;
  }

  private List<String> splitConditionTexts(String conditions) {
    if (conditions == null || conditions.isBlank()) return List.of();
    String normalized = conditions.trim();
    if ("-".equals(normalized)
        || "없음".equals(normalized)
        || "해당없음".equals(normalized)
        || "우대조건 없음".equals(normalized)) {
      return List.of();
    }
    return Arrays.stream(conditions.replace('\n', ' ').split("(?=\\d+\\.(?!\\d))|(?=[①-⑩])"))
        .map(String::trim)
        .filter(condition -> !condition.isBlank())
        .toList();
  }

  private long parseKoreanAmount(String numberText, String unit) {
    BigDecimal number = new BigDecimal(numberText);
    BigDecimal multiplier =
        switch (unit) {
          case "억" -> BigDecimal.valueOf(100_000_000L);
          case "천만" -> BigDecimal.valueOf(10_000_000L);
          case "백만" -> BigDecimal.valueOf(1_000_000L);
          default -> BigDecimal.valueOf(10_000L);
        };
    return number.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValue();
  }

  private String joinEligibility(FinlifeProduct product) {
    if (product.joinMember().isBlank() || "-".equals(product.joinMember())) {
      return "가입 대상은 금융기관 상품설명서를 확인하세요.";
    }
    return product.joinMember();
  }

  private String recommendationReason(FinlifeProduct product, InvestmentPlan plan, long targetAmount) {
    if (plan.getGoalAmount() == 0) {
      return product.companyName()
          + "에서 공시한 실제 상품이며, 목표 금액 없이 현재 안전한 가용 금액과 기간을 기준으로 비교했어요.";
    }
    if (product.termMonths() <= plan.getGoalMonths() && targetAmount > 0) {
      return product.companyName()
          + "에서 공시한 실제 상품이며, 목표 기간과 안전한 월 운용 가능액을 기준으로 비교했어요.";
    }
    return product.companyName() + "에서 공시한 실제 상품입니다. 목표 기간과 만기를 함께 확인하세요.";
  }

  private PlanStatus determineStatus(
      long goalAmount, long emergencyFundGap, long requiredMonthlySaving, long safeMonthlyCapacity) {
    if (goalAmount == 0) return PlanStatus.GOAL_NOT_SET;
    if (requiredMonthlySaving <= safeMonthlyCapacity && emergencyFundGap == 0) {
      return PlanStatus.ACHIEVABLE;
    }
    if (requiredMonthlySaving <= safeMonthlyCapacity) return PlanStatus.ADJUSTABLE;
    return PlanStatus.DIFFICULT;
  }

  private String createInterpretation(
      InvestmentPlan plan,
      PlanStatus status,
      long emergencyFundGap,
      long requiredMonthlySaving,
      long safeMonthlyCapacity) {
    if (status == PlanStatus.GOAL_NOT_SET) {
      return "목표 금액이 없어, 현재 안전한 월 운용 가능액과 기간을 기준으로 상품을 비교해보세요.";
    }
    if (status == PlanStatus.ACHIEVABLE) {
      return "현재 현금흐름으로 목표에 필요한 월 납입액을 감당할 수 있어요.";
    }
    if (status == PlanStatus.ADJUSTABLE) {
      return "목표는 달성 가능하지만, 비상금이 부족해 목표 자금과 안전망을 함께 나누는 계획이 필요해요.";
    }
    long difference = requiredMonthlySaving - safeMonthlyCapacity;
    return "현재 조건에서는 매월 "
        + String.format("%,d", difference)
        + "원을 더 마련하거나 목표 기간을 연장해야 해요."
        + (emergencyFundGap > 0 ? " 비상금도 먼저 보완하는 편이 안전해요." : "");
  }

  private long expectedAmount(long initialAmount, long monthlyDeposit, int months, BigDecimal annualRate) {
    BigDecimal monthlyRate = annualRate.divide(MONTHS_IN_YEAR, 10, RoundingMode.HALF_UP);
    BigDecimal balance = BigDecimal.valueOf(initialAmount);
    BigDecimal deposit = BigDecimal.valueOf(monthlyDeposit);
    for (int month = 0; month < months; month++) {
      balance = balance.multiply(BigDecimal.ONE.add(monthlyRate)).add(deposit);
    }
    return balance.setScale(0, RoundingMode.HALF_UP).longValue();
  }

  private long divideCeiling(long value, int divisor) {
    return value / divisor + (value % divisor == 0 ? 0 : 1);
  }

  private long safeAdd(long left, long right) {
    if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
    return left + right;
  }

  private long multiplySafely(long value, long multiplier) {
    if (value == 0 || multiplier == 0) return 0;
    if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
    return value * multiplier;
  }

}
