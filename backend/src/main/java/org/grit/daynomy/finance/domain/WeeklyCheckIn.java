package org.grit.daynomy.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "weekly_check_ins",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_weekly_check_ins_member_week",
            columnNames = {"member_id", "week_start"}))
public class WeeklyCheckIn extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "week_start", nullable = false)
  private LocalDate weekStart;

  @Column(name = "target_savings", nullable = false, precision = 19, scale = 2)
  private BigDecimal targetSavings;

  @Column(name = "target_investment", nullable = false, precision = 19, scale = 2)
  private BigDecimal targetInvestment;

  @Column(name = "target_debt_payment", nullable = false, precision = 19, scale = 2)
  private BigDecimal targetDebtPayment;

  @Column(name = "actual_savings", nullable = false, precision = 19, scale = 2)
  private BigDecimal actualSavings;

  @Column(name = "actual_investment", nullable = false, precision = 19, scale = 2)
  private BigDecimal actualInvestment;

  @Column(name = "actual_debt_payment", nullable = false, precision = 19, scale = 2)
  private BigDecimal actualDebtPayment;

  @Column(length = 200)
  private String note;

  private WeeklyCheckIn(Member member, LocalDate weekStart) {
    this.member = Objects.requireNonNull(member);
    this.weekStart = Objects.requireNonNull(weekStart);
  }

  public static WeeklyCheckIn create(Member member, LocalDate weekStart) {
    return new WeeklyCheckIn(member, weekStart);
  }

  public void change(
      BigDecimal targetSavings,
      BigDecimal targetInvestment,
      BigDecimal targetDebtPayment,
      BigDecimal actualSavings,
      BigDecimal actualInvestment,
      BigDecimal actualDebtPayment,
      String note) {
    this.targetSavings = Objects.requireNonNull(targetSavings);
    this.targetInvestment = Objects.requireNonNull(targetInvestment);
    this.targetDebtPayment = Objects.requireNonNull(targetDebtPayment);
    this.actualSavings = Objects.requireNonNull(actualSavings);
    this.actualInvestment = Objects.requireNonNull(actualInvestment);
    this.actualDebtPayment = Objects.requireNonNull(actualDebtPayment);
    this.note = note == null || note.isBlank() ? null : note.trim();
  }
}
