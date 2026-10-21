package org.grit.daynomy.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "investment_reviews")
public class InvestmentReview {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private InvestmentDecision transaction;

  @Column(name = "actual_result", nullable = false, length = 500)
  private String actualResult;

  @Column(name = "difference_from_expectation", nullable = false, length = 500)
  private String differenceFromExpectation;

  @Column(name = "next_action", nullable = false, length = 500)
  private String nextAction;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // 최초 작성 시 생성·수정 시각의 차이로 수정됨을 오인하지 않도록 함께 설정한다.
  @PrePersist
  void initializeTimestamps() {
    createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    updatedAt = createdAt;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public InvestmentReview(
      InvestmentDecision transaction,
      String actualResult,
      String differenceFromExpectation,
      String nextAction) {
    this.transaction = Objects.requireNonNull(transaction);
    this.actualResult = requireText(actualResult);
    this.differenceFromExpectation = requireText(differenceFromExpectation);
    this.nextAction = requireText(nextAction);
  }

  public void revise(String actualResult, String differenceFromExpectation, String nextAction) {
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
