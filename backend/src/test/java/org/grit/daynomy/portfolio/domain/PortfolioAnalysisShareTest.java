package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisShareTest {

  private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

  @Test
  void renewsTheLinkWithoutChangingItsSourceAnalysis() {
    var share = share();

    share.renew("  반도체 비중이 너무 높은지 의견을 받고 싶어요.  ", "b".repeat(64), snapshot(), NOW.plusSeconds(100));

    assertThat(share.getAnalysisId()).isEqualTo(7L);
    assertThat(share.getQuestion()).isEqualTo("반도체 비중이 너무 높은지 의견을 받고 싶어요.");
    assertThat(share.getTokenHash()).isEqualTo("b".repeat(64));
    assertThat(share.status(NOW)).isEqualTo(PortfolioAnalysisShare.Status.ACTIVE);
  }

  @Test
  void acceptsAShortQuestion() {
    var share = share();

    share.renew("어때?", "b".repeat(64), snapshot(), NOW.plusSeconds(100));

    assertThat(share.getQuestion()).isEqualTo("어때?");
  }

  @Test
  void revokedAndExpiredLinksAreNotActive() {
    var expired = share();
    var revoked = share();

    revoked.revoke(NOW);

    assertThat(expired.status(NOW.plusSeconds(61)))
        .isEqualTo(PortfolioAnalysisShare.Status.EXPIRED);
    assertThat(revoked.status(NOW)).isEqualTo(PortfolioAnalysisShare.Status.REVOKED);
  }

  @Test
  void rejectsMarkupInQuestionsAndComments() {
    Member commenter =
        Member.createGoogleMember("commenter", "commenter@example.com", "댓글 작성자", "");

    assertThatThrownBy(
            () ->
                share()
                    .renew(
                        "<b>이 질문은 허용되지 않아요</b>", "c".repeat(64), snapshot(), NOW.plusSeconds(100)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PortfolioAnalysisComment.root(share(), commenter, "<b>나쁜 입력입니다</b>"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void commentsAllowOnlyOneReplyLevelAndRejectInvalidContent() {
    Member commenter = Member.createGoogleMember("commenter", "comment@example.com", "댓글 작성자", "");
    PortfolioAnalysisComment comment =
        PortfolioAnalysisComment.root(share(), commenter, "포트폴리오 비중에 대한 의견입니다.");
    PortfolioAnalysisComment reply =
        PortfolioAnalysisComment.reply(comment, commenter, "  확인했습니다.  ");

    assertThat(reply.getContent()).isEqualTo("확인했습니다.");
    assertThatThrownBy(() -> PortfolioAnalysisComment.reply(reply, commenter, "대대댓글"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reply.update("한")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reply.update("😀")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reply.update("<b>수정</b>"))
        .isInstanceOf(IllegalArgumentException.class);
    reply.update("  😀😀  ");
    assertThat(reply.getContent()).isEqualTo("😀😀");
  }

  private PortfolioAnalysisShare share() {
    Member owner = Member.createGoogleMember("owner", "owner@example.com", "작성자", "");
    return PortfolioAnalysisShare.create(
        owner, 7L, "반도체 종목 비중에 대한 의견을 받고 싶어요.", "a".repeat(64), snapshot(), NOW.plusSeconds(60));
  }

  private PortfolioAnalysisPublicSnapshot snapshot() {
    return new PortfolioAnalysisPublicSnapshot(
        1,
        1,
        true,
        true,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        NOW,
        "분석 결과",
        List.of(),
        List.of(),
        List.of());
  }
}
