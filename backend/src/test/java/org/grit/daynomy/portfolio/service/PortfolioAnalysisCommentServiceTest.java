package org.grit.daynomy.portfolio.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisCommentServiceTest {

  private final PortfolioAnalysisShareService shares = mock(PortfolioAnalysisShareService.class);
  private final PortfolioAnalysisCommentRepository comments =
      mock(PortfolioAnalysisCommentRepository.class);
  private final MemberRepository members = mock(MemberRepository.class);
  private final PortfolioAnalysisShareRateLimiter rateLimiter =
      mock(PortfolioAnalysisShareRateLimiter.class);
  private final PortfolioAnalysisShareResponseFactory responses =
      mock(PortfolioAnalysisShareResponseFactory.class);
  private final PortfolioAnalysisCommentService service =
      new PortfolioAnalysisCommentService(shares, comments, members, rateLimiter, responses);

  @Test
  void createsCommentAfterCheckingActiveShareAccess() {
    PortfolioAnalysisShare share = mock(PortfolioAnalysisShare.class);
    Member owner = mock(Member.class);
    Member commenter = mock(Member.class);
    when(share.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(1L);
    when(shares.requirePublicAccessForUpdate("token")).thenReturn(share);
    when(members.getReferenceById(2L)).thenReturn(commenter);
    when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create("token", 2L, new PortfolioAnalysisShareDto.CommentRequest("분산 비중을 검토해 주세요."));

    verify(rateLimiter).acquireWrite(2L);
    verify(shares).requirePublicAccessForUpdate("token");
    verify(comments).save(any(PortfolioAnalysisComment.class));
  }

  @Test
  void deletesOwnCommentWithoutRequiringAnActiveShare() {
    PortfolioAnalysisShare share = mock(PortfolioAnalysisShare.class);
    PortfolioAnalysisComment comment = mock(PortfolioAnalysisComment.class);
    when(share.getId()).thenReturn(3L);
    when(shares.findPublicForUpdate("token")).thenReturn(share);
    when(comments.findByIdAndAuthorId(9L, 2L)).thenReturn(Optional.of(comment));
    when(comment.getShare()).thenReturn(share);
    when(comment.isReply()).thenReturn(false);

    service.delete("token", 2L, 9L);

    verify(shares).findPublicForUpdate("token");
    verify(shares, never()).requirePublicAccessForUpdate("token");
    verify(comments).delete(comment);
  }
}
