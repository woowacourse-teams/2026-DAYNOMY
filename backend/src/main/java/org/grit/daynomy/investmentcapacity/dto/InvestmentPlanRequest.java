package org.grit.daynomy.investmentcapacity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record InvestmentPlanRequest(
    @NotNull(message = "생년월일을 입력해주세요.") @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
        LocalDate birthDate,
    @PositiveOrZero(message = "월 소득은 0원 이상이어야 합니다.") long monthlyIncome,
    @PositiveOrZero(message = "고정지출은 0원 이상이어야 합니다.") long monthlyFixedExpense,
    @PositiveOrZero(message = "변동지출은 0원 이상이어야 합니다.") long monthlyVariableExpense,
    @PositiveOrZero(message = "비정기 지출 대비금은 0원 이상이어야 합니다.")
        long irregularExpenseReserve,
    @PositiveOrZero(message = "비상금 적립액은 0원 이상이어야 합니다.") long emergencyFundContribution,
    @PositiveOrZero(message = "월 부채 상환액은 0원 이상이어야 합니다.") long monthlyDebtRepayment,
    @PositiveOrZero(message = "현재 현금은 0원 이상이어야 합니다.") long currentCash,
    @PositiveOrZero(message = "기존 예금·적금은 0원 이상이어야 합니다.") long existingDepositSavings,
    @PositiveOrZero(message = "투자자산은 0원 이상이어야 합니다.") long investmentAssets,
    @PositiveOrZero(message = "기타 자산은 0원 이상이어야 합니다.") long otherAssets,
    @PositiveOrZero(message = "목표 금액은 0원 이상이어야 합니다.") long goalAmount,
    @Min(value = 1, message = "목표 기간은 1개월 이상이어야 합니다.")
        @Max(value = 600, message = "목표 기간은 600개월 이하여야 합니다.")
        int goalMonths,
    String selectedScenario,
    String selectedProductId) {}
