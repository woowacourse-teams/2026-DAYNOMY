package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 변화 없는 재확인마다 행을 추가하지 않고 최근 확인 상태만 보관한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "portfolio_analysis_checks")
public class PortfolioAnalysisCheck {

  @Id
  @Column(name = "portfolio_id")
  private Long portfolioId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "portfolio_id")
  private Portfolio portfolio;

  @Column(name = "last_succeeded_at")
  private Instant lastSucceededAt;

  @Column(name = "last_failed_at")
  private Instant lastFailedAt;

  @Column(name = "failure_code", length = 100)
  private String failureCode;

  public PortfolioAnalysisCheck(Portfolio portfolio) {
    this.portfolio = Objects.requireNonNull(portfolio);
  }

  public void recordSuccess(Instant checkedAt) {
    Objects.requireNonNull(checkedAt);
    if (lastSucceededAt == null || checkedAt.isAfter(lastSucceededAt)) {
      lastSucceededAt = checkedAt;
    }
  }

  public void recordFailure(Instant failedAt, String errorCode) {
    Objects.requireNonNull(failedAt);
    if (errorCode == null || !errorCode.matches("[A-Z][A-Z0-9_]{0,99}")) {
      throw new IllegalArgumentException("실패 원인은 예외 메시지가 아닌 오류 코드로 저장해야 합니다.");
    }
    if (lastFailedAt == null || failedAt.isAfter(lastFailedAt)) {
      lastFailedAt = failedAt;
      failureCode = errorCode;
    }
  }
}
