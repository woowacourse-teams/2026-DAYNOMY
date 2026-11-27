package org.grit.daynomy.portfolio.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioAnalysisCommentService {

  private final PortfolioAnalysisShareService shares;
  private final PortfolioAnalysisCommentRepository comments;
  private final MemberRepository members;
  private final PortfolioAnalysisShareRateLimiter rateLimiter;
  private final PortfolioAnalysisShareResponseFactory responses;

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse create(
      String rawToken, Long memberId, PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = shares.requirePublicAccessForUpdate(rawToken);
    if (share.getOwner().getId().equals(memberId)) {
      throw new BusinessException(
          PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_OWNER_COMMENT_FORBIDDEN);
    }
    PortfolioAnalysisComment comment =
        PortfolioAnalysisComment.root(share, members.getReferenceById(memberId), request.content());
    return responses.commentResponse(comments.save(comment), memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse update(
      String rawToken,
      Long memberId,
      Long commentId,
      PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = shares.requirePublicAccessForUpdate(rawToken);
    PortfolioAnalysisComment comment = ownedRootComment(memberId, commentId, share.getId());
    comment.update(request.content());
    return responses.commentResponse(comment, memberId);
  }

  @Transactional
  public void delete(String rawToken, Long memberId, Long commentId) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = shares.findPublicForUpdate(rawToken);
    comments.delete(ownedRootComment(memberId, commentId, share.getId()));
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse saveOwnerReply(
      Long memberId,
      Long shareId,
      Long commentId,
      PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    shares.requireOwnerAccess(memberId, shareId);
    PortfolioAnalysisComment comment = rootComment(commentId, shareId);
    comments.save(
        PortfolioAnalysisComment.reply(
            comment, members.getReferenceById(memberId), request.content()));
    return responses.commentResponse(comment, memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse createCommenterReply(
      String rawToken,
      Long memberId,
      Long commentId,
      PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = shares.requirePublicAccessForUpdate(rawToken);
    PortfolioAnalysisComment comment =
        comments
            .findByIdAndShareId(commentId, share.getId())
            .filter(candidate -> !candidate.isReply())
            .filter(candidate -> candidate.getAuthor().getId().equals(memberId))
            .orElseThrow(this::commentNotFound);
    comments.save(
        PortfolioAnalysisComment.reply(
            comment, members.getReferenceById(memberId), request.content()));
    return responses.commentResponse(comment, memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse updateReply(
      Long memberId, Long replyId, PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisComment reply = ownedReply(memberId, replyId);
    reply.update(request.content());
    return responses.commentResponse(reply.getParent(), memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse deleteReply(Long memberId, Long replyId) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisComment reply = ownedReply(memberId, replyId);
    PortfolioAnalysisComment parent = reply.getParent();
    comments.delete(reply);
    comments.flush();
    return responses.commentResponse(parent, memberId);
  }

  private PortfolioAnalysisComment rootComment(Long commentId, Long shareId) {
    return comments
        .findByIdAndShareId(commentId, shareId)
        .filter(candidate -> !candidate.isReply())
        .orElseThrow(this::commentNotFound);
  }

  private PortfolioAnalysisComment ownedRootComment(Long memberId, Long commentId, Long shareId) {
    return comments
        .findByIdAndAuthorId(commentId, memberId)
        .filter(comment -> !comment.isReply())
        .filter(comment -> comment.getShare().getId().equals(shareId))
        .orElseThrow(this::commentNotFound);
  }

  private PortfolioAnalysisComment ownedReply(Long memberId, Long replyId) {
    return comments
        .findByIdAndAuthorId(replyId, memberId)
        .filter(PortfolioAnalysisComment::isReply)
        .orElseThrow(
            () ->
                new BusinessException(
                    PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_REPLY_NOT_FOUND));
  }

  private BusinessException commentNotFound() {
    return new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND);
  }
}
