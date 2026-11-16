package org.grit.daynomy.portfolio.domain;

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
import java.time.Instant;
import java.util.Objects;
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
    name = "portfolio_analysis_shares",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_member_id", "analysis_id"}))
public class PortfolioAnalysisShare extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_member_id", nullable = false, updatable = false)
  private Member owner;

  @Column(name = "analysis_id", updatable = false)
  private Long analysisId;

  @Column(nullable = false, length = 200)
  private String question;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private PortfolioAnalysisPublicSnapshot snapshot;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  private PortfolioAnalysisShare(
      Member owner,
      Long analysisId,
      String question,
      String tokenHash,
      PortfolioAnalysisPublicSnapshot snapshot,
      Instant expiresAt) {
    this.owner = Objects.requireNonNull(owner);
    this.analysisId = analysisId;
    renew(question, tokenHash, snapshot, expiresAt);
  }

  public static PortfolioAnalysisShare create(
      Member owner,
      Long analysisId,
      String question,
      String tokenHash,
      PortfolioAnalysisPublicSnapshot snapshot,
      Instant expiresAt) {
    return new PortfolioAnalysisShare(owner, analysisId, question, tokenHash, snapshot, expiresAt);
  }

  public void renew(
      String question,
      String tokenHash,
      PortfolioAnalysisPublicSnapshot snapshot,
      Instant expiresAt) {
    String normalized = Objects.requireNonNull(question).strip();
    if (normalized.isEmpty() || normalized.length() > 200 || containsMarkup(normalized)) {
      throw new IllegalArgumentException("공유 질문은 1자 이상 200자 이하의 일반 텍스트여야 합니다.");
    }
    this.question = normalized;
    this.tokenHash = requireHash(tokenHash);
    this.snapshot = Objects.requireNonNull(snapshot);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.revokedAt = null;
  }

  public void revoke(Instant now) {
    if (revokedAt == null) revokedAt = Objects.requireNonNull(now);
  }

  public Status status(Instant now) {
    if (revokedAt != null) return Status.REVOKED;
    if (!expiresAt.isAfter(now)) return Status.EXPIRED;
    return Status.ACTIVE;
  }

  private static String requireHash(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("공유 토큰 해시는 SHA-256 형식이어야 합니다.");
    }
    return value;
  }

  private static boolean containsMarkup(String value) {
    return value.indexOf('<') >= 0 || value.indexOf('>') >= 0;
  }

  public enum Status {
    ACTIVE,
    EXPIRED,
    REVOKED
  }
}
