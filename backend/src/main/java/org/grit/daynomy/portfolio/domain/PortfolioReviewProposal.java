package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "portfolio_review_proposals")
public class PortfolioReviewProposal extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "share_id", nullable = false, updatable = false)
  private PortfolioAnalysisShare share;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "comment_id", nullable = false, unique = true, updatable = false)
  private PortfolioAnalysisComment comment;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private PortfolioReviewProposalSnapshot snapshot;

  private PortfolioReviewProposal(
      PortfolioAnalysisShare share,
      PortfolioAnalysisComment comment,
      PortfolioReviewProposalSnapshot snapshot) {
    this.share = Objects.requireNonNull(share);
    this.comment = Objects.requireNonNull(comment);
    if (!Objects.equals(share.getId(), comment.getShare().getId())) {
      throw new IllegalArgumentException("검토안 댓글은 같은 공유 링크에 속해야 합니다.");
    }
    update(snapshot);
  }

  public static PortfolioReviewProposal create(
      PortfolioAnalysisShare share,
      PortfolioAnalysisComment comment,
      PortfolioReviewProposalSnapshot snapshot) {
    return new PortfolioReviewProposal(share, comment, snapshot);
  }

  public void update(PortfolioReviewProposalSnapshot snapshot) {
    this.snapshot = Objects.requireNonNull(snapshot);
  }
}
