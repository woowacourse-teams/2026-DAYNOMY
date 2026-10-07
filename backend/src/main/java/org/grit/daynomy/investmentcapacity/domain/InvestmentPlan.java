package org.grit.daynomy.investmentcapacity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.investmentcapacity.dto.InvestmentPlanRequest;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "investment_plans",
    uniqueConstraints = @UniqueConstraint(name = "uk_investment_plans_member", columnNames = "member_id"))
public class InvestmentPlan extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Column(name = "monthly_income", nullable = false)
  private long monthlyIncome;

  @Column(name = "monthly_fixed_expense", nullable = false)
  private long monthlyFixedExpense;

  @Column(name = "monthly_variable_expense", nullable = false)
  private long monthlyVariableExpense;

  @Column(name = "irregular_expense_reserve", nullable = false)
  private long irregularExpenseReserve;

  @Column(name = "emergency_fund_contribution", nullable = false)
  private long emergencyFundContribution;

  @Column(name = "monthly_debt_repayment", nullable = false)
  private long monthlyDebtRepayment;

  @Column(name = "current_cash", nullable = false)
  private long currentCash;

  @Column(name = "existing_deposit_savings", nullable = false)
  private long existingDepositSavings;

  @Column(name = "investment_assets", nullable = false)
  private long investmentAssets;

  @Column(name = "other_assets", nullable = false)
  private long otherAssets;

  @Column(name = "goal_amount", nullable = false)
  private long goalAmount;

  @Column(name = "goal_months", nullable = false)
  private int goalMonths;

  @Column(name = "selected_scenario", length = 20)
  private String selectedScenario;

  @Column(name = "selected_product_id", length = 50)
  private String selectedProductId;

  private InvestmentPlan(Member member, InvestmentPlanRequest request) {
    this.member = member;
    update(request);
  }

  public static InvestmentPlan create(Member member, InvestmentPlanRequest request) {
    return new InvestmentPlan(member, request);
  }

  public void update(InvestmentPlanRequest request) {
    this.birthDate = request.birthDate();
    this.monthlyIncome = request.monthlyIncome();
    this.monthlyFixedExpense = request.monthlyFixedExpense();
    this.monthlyVariableExpense = request.monthlyVariableExpense();
    this.irregularExpenseReserve = request.irregularExpenseReserve();
    this.emergencyFundContribution = request.emergencyFundContribution();
    this.monthlyDebtRepayment = request.monthlyDebtRepayment();
    this.currentCash = request.currentCash();
    this.existingDepositSavings = request.existingDepositSavings();
    this.investmentAssets = request.investmentAssets();
    this.otherAssets = request.otherAssets();
    this.goalAmount = request.goalAmount();
    this.goalMonths = request.goalMonths();
    this.selectedScenario = request.selectedScenario();
    this.selectedProductId = request.selectedProductId();
  }
}
