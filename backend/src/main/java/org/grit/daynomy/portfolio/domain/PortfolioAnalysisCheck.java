package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
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

  public enum Outcome {
    UPDATED,
    UNCHANGED,
    UNCONFIRMED
  }

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "last_outcome", length = 30)
  private Outcome lastOutcome;

  public boolean requiresRecheck(Instant now) {
    return lastOutcome == Outcome.UNCONFIRMED
        || lastSucceededAt == null
        || !now.isBefore(lastSucceededAt.plus(Duration.ofHours(24)));
  }

  public void recordSuccess(Instant checkedAt, Outcome outcome) {
    checkedAt = Objects.requireNonNull(checkedAt).truncatedTo(ChronoUnit.MICROS);
    if (lastSucceededAt == null || checkedAt.isAfter(lastSucceededAt)) {
      recordSuccess(checkedAt);
      lastOutcome = Objects.requireNonNull(outcome);
    }
  }

  @Column(name = "request_token")
  private UUID requestToken;

  @Column(name = "request_expires_at")
  private Instant requestExpiresAt;

  public PortfolioAnalysisCheck(Portfolio portfolio) {
    this.portfolio = Objects.requireNonNull(portfolio);
  }

  public boolean isProcessing(Instant now) {
    return requestToken != null && requestExpiresAt.isAfter(now);
  }

  public boolean begin(UUID token, Instant now, Duration timeout) {
    if (isProcessing(now)) {
      return false;
    }
    requestToken = Objects.requireNonNull(token);
    requestExpiresAt = now.plus(timeout).truncatedTo(ChronoUnit.MICROS);
    return true;
  }

  public boolean ownsRequest(UUID token, Instant now) {
    return token != null && token.equals(requestToken) && isProcessing(now);
  }

  public void finish(UUID token) {
    if (token != null && token.equals(requestToken)) {
      requestToken = null;
      requestExpiresAt = null;
    }
  }

  public void recordSuccess(Instant checkedAt) {
    checkedAt = Objects.requireNonNull(checkedAt).truncatedTo(ChronoUnit.MICROS);
    if (lastSucceededAt == null || checkedAt.isAfter(lastSucceededAt)) {
      lastSucceededAt = checkedAt;
    }
  }

  public void recordFailure(Instant failedAt, String errorCode) {
    failedAt = Objects.requireNonNull(failedAt).truncatedTo(ChronoUnit.MICROS);
    if (errorCode == null || !errorCode.matches("[A-Z][A-Z0-9_]{0,99}")) {
      throw new IllegalArgumentException("실패 원인은 예외 메시지가 아닌 오류 코드로 저장해야 합니다.");
    }
    if (lastFailedAt == null || failedAt.isAfter(lastFailedAt)) {
      lastFailedAt = failedAt;
      failureCode = errorCode;
    }
  }
}
