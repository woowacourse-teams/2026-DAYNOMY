package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisShareRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PortfolioAnalysisShareVisibilityTest {
  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {1, 2, 3})
  void onlyOwnerReceivesAllComments(Long viewerId) {
    var shares = mock(PortfolioAnalysisShareRepository.class);
    var comments = mock(PortfolioAnalysisCommentRepository.class);
    var share = mock(PortfolioAnalysisShare.class);
    var owner = mock(Member.class);
    var reviewer = mock(Member.class);
    var comment = mock(PortfolioAnalysisComment.class);
    var now = Instant.parse("2026-11-14T00:00:00Z");
    when(shares.findByTokenHash(anyString())).thenReturn(Optional.of(share));
    when(share.getId()).thenReturn(10L);
    when(share.getOwner()).thenReturn(owner);
    when(share.getAdditionalExplanation()).thenReturn("링크를 가진 모두에게 공개하는 투자 배경");
    when(share.getAdditionalExplanationUpdatedAt()).thenReturn(now);
    when(owner.getId()).thenReturn(1L);
    when(share.status(now)).thenReturn(PortfolioAnalysisShare.Status.ACTIVE);
    when(share.getSnapshot())
        .thenReturn(
            new PortfolioAnalysisPublicSnapshot(
                0, 0, true, false, null, null, null, null, null, "", List.of(), List.of(),
                List.of()));
    when(comment.getId()).thenReturn(20L);
    when(comment.getShare()).thenReturn(share);
    when(comment.getAuthor()).thenReturn(reviewer);
    when(comment.getContent()).thenReturn("리뷰어의 비공개 의견");
    when(comments.findAllByShareIdInAndParentIsNullOrderByCreatedAtAscIdAsc(List.of(10L)))
        .thenReturn(List.of(comment));
    when(comments.findAllByShareIdAndAuthorIdAndParentIsNullOrderByCreatedAtAscIdAsc(10L, 2L))
        .thenReturn(List.of(comment));
    var cipher = mock(PortfolioShareTokenCipher.class);
    var responses = new PortfolioAnalysisShareResponseFactory(comments, cipher);
    var service =
        new PortfolioAnalysisShareService(
            shares,
            comments,
            null,
            null,
            null,
            null,
            null,
            mock(PortfolioAnalysisShareRateLimiter.class),
            cipher,
            Clock.fixed(now, ZoneOffset.UTC),
            null,
            mock(PortfolioAnalysisShareSnapshotFactory.class),
            responses);
    var response = service.getPublic("test-token", viewerId, "127.0.0.1");
    assertThat(response.additionalExplanation()).isEqualTo("링크를 가진 모두에게 공개하는 투자 배경");
    assertThat(response.additionalExplanationUpdatedAt()).isEqualTo(now);
    if (Long.valueOf(1).equals(viewerId)) {
      assertThat(response.ownerShare().id()).isEqualTo(10L);
      assertThat(response.ownerShare().comments())
          .extracting(value -> value.content())
          .containsExactly("리뷰어의 비공개 의견");
      assertThat(response.canComment()).isFalse();
    } else {
      assertThat(response.ownerShare()).isNull();
      verify(comments, never())
          .findAllByShareIdInAndParentIsNullOrderByCreatedAtAscIdAsc(List.of(10L));
    }
    assertThat(response.myComments()).hasSize(Long.valueOf(2).equals(viewerId) ? 1 : 0);
  }
}
