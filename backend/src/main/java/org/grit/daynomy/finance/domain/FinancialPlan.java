package org.grit.daynomy.finance.domain;

import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "financial_plans",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "plan_key"}))
public class FinancialPlan extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "plan_key", nullable = false, length = 80)
  private String planKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Content content;

  public FinancialPlan(Member member, String planKey, Content content) {
    this.member = member;
    this.planKey = planKey;
    change(content);
  }

  public void change(Content content) {
    if (content == null || content.monthlyPlan() == null || !content.monthlyPlan().balanced()) {
      throw new IllegalArgumentException("월 배분 합계가 사용 가능한 금액과 다릅니다.");
    }
    this.content = content;
  }

  public record Action(String id, String label, boolean completed) {}

  public record Allocation(
      long monthlyIncome,
      long monthlyExpenses,
      long goalAmount,
      long goalSaved,
      int goalMonths,
      long savings,
      long emergency,
      long investment,
      long debt) {
    public boolean balanced() {
      long[] amounts = {
        monthlyIncome, monthlyExpenses, goalAmount, goalSaved, savings, emergency, investment, debt
      };
      for (long amount : amounts) if (amount < 0 || amount > 1_000_000_000_000L) return false;
      return goalMonths >= 1
          && goalMonths <= 120
          && savings + emergency + investment + debt
              == Math.max(0, monthlyIncome - monthlyExpenses);
    }
  }

  public record Content(
      String topic,
      String title,
      String summary,
      List<String> details,
      List<Action> actions,
      Allocation monthlyPlan) {}
}
