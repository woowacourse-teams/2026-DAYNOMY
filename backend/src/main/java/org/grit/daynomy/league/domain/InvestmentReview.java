package org.grit.daynomy.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "investment_reviews")
public class InvestmentReview extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private PortfolioTransaction transaction;

  @Column(name = "actual_result", nullable = false, length = 500)
  private String actualResult;

  @Column(name = "difference_from_expectation", nullable = false, length = 500)
  private String differenceFromExpectation;

  @Column(name = "next_action", nullable = false, length = 500)
  private String nextAction;

  public InvestmentReview(
      PortfolioTransaction transaction,
      String actualResult,
      String differenceFromExpectation,
      String nextAction) {
    this.transaction = Objects.requireNonNull(transaction);
    this.actualResult = requireText(actualResult);
    this.differenceFromExpectation = requireText(differenceFromExpectation);
    this.nextAction = requireText(nextAction);
  }

  private String requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("복기 내용은 비어 있을 수 없습니다.");
    }
    return value.trim();
  }
}
