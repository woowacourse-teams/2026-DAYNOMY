package org.grit.daynomy.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.finance.domain.FinancialPlan;

public final class FinancialPlanDto {
  private FinancialPlanDto() {}

  public record Action(
      @NotBlank @Size(max = 80) String id,
      @NotBlank @Size(max = 200) String label,
      boolean completed) {}

  public record MonthlyPlan(
      @Min(0) @Max(1_000_000_000_000L) long monthlyIncome,
      @Min(0) @Max(1_000_000_000_000L) long monthlyExpenses,
      @Min(0) @Max(1_000_000_000_000L) long goalAmount,
      @Min(0) @Max(1_000_000_000_000L) long goalSaved,
      @Min(1) @Max(120) int goalMonths,
      @Min(0) @Max(1_000_000_000_000L) long savings,
      @Min(0) @Max(1_000_000_000_000L) long emergency,
      @Min(0) @Max(1_000_000_000_000L) long investment,
      @Min(0) @Max(1_000_000_000_000L) long debt) {
    public FinancialPlan.Allocation toDomain() {
      return new FinancialPlan.Allocation(
          monthlyIncome,
          monthlyExpenses,
          goalAmount,
          goalSaved,
          goalMonths,
          savings,
          emergency,
          investment,
          debt);
    }
  }

  public record PlanRequest(
      @NotBlank @Pattern(regexp = "예금·적금 비교|청년미래적금|저축·투자 계획") String topic,
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 300) String summary,
      @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> details,
      @NotNull @Size(max = 20) List<@NotNull @Valid Action> actions,
      @NotNull @Valid MonthlyPlan monthlyPlan) {
    public FinancialPlan.Content toDomain() {
      return new FinancialPlan.Content(
          topic,
          title,
          summary,
          List.copyOf(details),
          actions.stream()
              .map(a -> new FinancialPlan.Action(a.id(), a.label(), a.completed()))
              .toList(),
          monthlyPlan.toDomain());
    }
  }

  public record PlanResponse(
      String id,
      String topic,
      String title,
      String summary,
      List<String> details,
      List<Action> actions,
      MonthlyPlan monthlyPlan,
      Instant createdAt) {
    public static PlanResponse from(FinancialPlan plan) {
      FinancialPlan.Content c = plan.getContent();
      FinancialPlan.Allocation m = c.monthlyPlan();
      return new PlanResponse(
          plan.getPlanKey(),
          c.topic(),
          c.title(),
          c.summary(),
          c.details(),
          c.actions().stream().map(a -> new Action(a.id(), a.label(), a.completed())).toList(),
          new MonthlyPlan(
              m.monthlyIncome(),
              m.monthlyExpenses(),
              m.goalAmount(),
              m.goalSaved(),
              m.goalMonths(),
              m.savings(),
              m.emergency(),
              m.investment(),
              m.debt()),
          plan.getCreatedAt());
    }
  }

  public record PlanListResponse(List<PlanResponse> plans) {}
}
