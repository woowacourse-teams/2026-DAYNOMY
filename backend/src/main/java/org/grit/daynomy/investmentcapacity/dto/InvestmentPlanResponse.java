package org.grit.daynomy.investmentcapacity.dto;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.investmentcapacity.domain.PlanStatus;

public record InvestmentPlanResponse(
    LocalDate birthDate,
    long monthlyIncome,
    long monthlyFixedExpense,
    long monthlyVariableExpense,
    long irregularExpenseReserve,
    long emergencyFundContribution,
    long monthlyDebtRepayment,
    long currentCash,
    long existingDepositSavings,
    long investmentAssets,
    long otherAssets,
    long totalAssets,
    long availableCurrentCash,
    long goalAmount,
    int goalMonths,
    long emergencyFundTarget,
    long emergencyFundGap,
    long safeMonthlyCapacity,
    long requiredMonthlySaving,
    PlanStatus status,
    String interpretation,
    String selectedScenario,
    String selectedProductId,
    List<InvestmentScenarioResponse> scenarios,
    List<InvestmentProductResponse> products) {}
