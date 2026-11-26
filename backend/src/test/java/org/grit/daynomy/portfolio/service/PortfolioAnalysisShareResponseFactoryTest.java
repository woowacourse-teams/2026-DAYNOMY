package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisShareResponseFactoryTest {

  private final PortfolioAnalysisCommentRepository comments =
      mock(PortfolioAnalysisCommentRepository.class);
  private final PortfolioAnalysisShareResponseFactory factory =
      new PortfolioAnalysisShareResponseFactory(comments, mock(PortfolioShareTokenCipher.class));

  @Test
  void loadsRootsAndRepliesOnceForTheWholeShareList() {
    Instant now = Instant.parse("2026-11-24T00:00:00Z");
    Member owner = member(1L, "작성자");
    Member reviewer = member(2L, "검토자");
    PortfolioAnalysisShare first = share(10L, owner, now);
    PortfolioAnalysisShare second = share(20L, owner, now);
    PortfolioAnalysisComment firstRoot = comment(100L, first, reviewer, null, "첫 번째 의견");
    PortfolioAnalysisComment secondRoot = comment(200L, second, reviewer, null, "두 번째 의견");
    PortfolioAnalysisComment reply = comment(300L, first, owner, firstRoot, "첫 번째 답글");
    when(comments.findAllByShareIdInAndParentIsNullOrderByCreatedAtAscIdAsc(List.of(10L, 20L)))
        .thenReturn(List.of(firstRoot, secondRoot));
    when(comments.findAllByParentIdInOrderByCreatedAtAscIdAsc(List.of(100L, 200L)))
        .thenReturn(List.of(reply));

    var responses = factory.ownerResponses(List.of(first, second), now);

    assertThat(responses).extracting(response -> response.comments().size()).containsExactly(1, 1);
    assertThat(responses.getFirst().comments().getFirst().replies())
        .extracting(response -> response.content())
        .containsExactly("첫 번째 답글");
    assertThat(responses.get(1).comments().getFirst().replies()).isEmpty();
    verify(comments, times(1))
        .findAllByShareIdInAndParentIsNullOrderByCreatedAtAscIdAsc(List.of(10L, 20L));
    verify(comments, times(1)).findAllByParentIdInOrderByCreatedAtAscIdAsc(List.of(100L, 200L));
  }

  private PortfolioAnalysisShare share(Long id, Member owner, Instant now) {
    PortfolioAnalysisShare share = mock(PortfolioAnalysisShare.class);
    when(share.getId()).thenReturn(id);
    when(share.getOwner()).thenReturn(owner);
    when(share.getQuestion()).thenReturn("포트폴리오 의견을 주세요.");
    when(share.getExpiresAt()).thenReturn(now.plusSeconds(3600));
    when(share.status(now)).thenReturn(PortfolioAnalysisShare.Status.ACTIVE);
    when(share.getSnapshot()).thenReturn(snapshot());
    return share;
  }

  private PortfolioAnalysisComment comment(
      Long id,
      PortfolioAnalysisShare share,
      Member author,
      PortfolioAnalysisComment parent,
      String content) {
    PortfolioAnalysisComment comment = mock(PortfolioAnalysisComment.class);
    when(comment.getId()).thenReturn(id);
    when(comment.getShare()).thenReturn(share);
    when(comment.getAuthor()).thenReturn(author);
    when(comment.getParent()).thenReturn(parent);
    when(comment.getContent()).thenReturn(content);
    return comment;
  }

  private Member member(Long id, String nickname) {
    Member member = mock(Member.class);
    when(member.getId()).thenReturn(id);
    when(member.getNickname()).thenReturn(nickname);
    return member;
  }

  private PortfolioAnalysisPublicSnapshot snapshot() {
    return new PortfolioAnalysisPublicSnapshot(
        0,
        0,
        true,
        false,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        "",
        List.of(),
        List.of(),
        List.of());
  }
}
