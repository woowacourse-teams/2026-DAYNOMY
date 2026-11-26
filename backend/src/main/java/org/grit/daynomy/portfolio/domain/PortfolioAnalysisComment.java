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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "portfolio_analysis_comments")
public class PortfolioAnalysisComment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "share_id", nullable = false, updatable = false)
  private PortfolioAnalysisShare share;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_member_id", nullable = false, updatable = false)
  private Member author;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_comment_id", updatable = false)
  private PortfolioAnalysisComment parent;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  private PortfolioAnalysisComment(
      PortfolioAnalysisShare share,
      Member author,
      PortfolioAnalysisComment parent,
      String content) {
    this.share = Objects.requireNonNull(share);
    this.author = Objects.requireNonNull(author);
    this.parent = parent;
    update(content);
  }

  public static PortfolioAnalysisComment root(
      PortfolioAnalysisShare share, Member author, String content) {
    return new PortfolioAnalysisComment(share, author, null, content);
  }

  public static PortfolioAnalysisComment reply(
      PortfolioAnalysisComment parent, Member author, String content) {
    PortfolioAnalysisComment root = Objects.requireNonNull(parent);
    if (root.parent != null) {
      throw new IllegalArgumentException("대댓글에는 답글을 작성할 수 없습니다.");
    }
    return new PortfolioAnalysisComment(root.share, author, root, content);
  }

  public boolean isReply() {
    return parent != null;
  }

  public void update(String content) {
    this.content = normalizeContent(content);
  }

  public static String normalizeContent(String content) {
    String normalized = Objects.requireNonNull(content).strip();
    int length = normalized.codePointCount(0, normalized.length());
    if (length < 2 || length > 1000 || containsMarkup(normalized)) {
      throw new IllegalArgumentException("댓글은 2자 이상 1000자 이하의 일반 텍스트여야 합니다.");
    }
    return normalized;
  }

  private static boolean containsMarkup(String value) {
    return value.indexOf('<') >= 0 || value.indexOf('>') >= 0;
  }
}
