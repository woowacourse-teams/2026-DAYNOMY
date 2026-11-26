package org.grit.daynomy.portfolio.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.SanitizedLogException;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class PortfolioAnalysisShareResponseFactory {

  private final PortfolioAnalysisCommentRepository comments;
  private final PortfolioShareTokenCipher tokenCipher;

  public List<PortfolioAnalysisShareDto.OwnerResponse> ownerResponses(
      List<PortfolioAnalysisShare> shares, Instant now) {
    if (shares.isEmpty()) return List.of();
    List<PortfolioAnalysisComment> roots =
        comments.findAllByShareIdInAndParentIsNullOrderByCreatedAtAscIdAsc(shareIds(shares));
    Map<Long, List<PortfolioAnalysisComment>> rootsByShare = rootsByShare(roots);
    Map<Long, List<PortfolioAnalysisComment>> repliesByParent = repliesByParent(roots);
    return shares.stream()
        .map(
            share ->
                ownerResponse(
                    share,
                    now,
                    rootsByShare.getOrDefault(share.getId(), List.of()),
                    repliesByParent))
        .toList();
  }

  public PortfolioAnalysisShareDto.OwnerResponse ownerResponse(
      PortfolioAnalysisShare share, Instant now) {
    return ownerResponses(List.of(share), now).getFirst();
  }

  public PortfolioAnalysisShareDto.PublicResponse publicResponse(
      PortfolioAnalysisShare share, Long viewerMemberId, Instant now) {
    boolean owner = share.getOwner().getId().equals(viewerMemberId);
    List<PortfolioAnalysisComment> myRoots =
        viewerMemberId == null || owner
            ? List.of()
            : comments.findAllByShareIdAndAuthorIdAndParentIsNullOrderByCreatedAtAscIdAsc(
                share.getId(), viewerMemberId);
    Map<Long, List<PortfolioAnalysisComment>> myReplies = repliesByParent(myRoots);
    return new PortfolioAnalysisShareDto.PublicResponse(
        share.getOwner().getNickname(),
        share.getQuestion(),
        share.getAdditionalExplanation(),
        share.getAdditionalExplanationUpdatedAt(),
        share.getExpiresAt(),
        PortfolioAnalysisShareDto.SnapshotResponse.from(share.getSnapshot()),
        viewerMemberId != null && !owner,
        commentResponses(myRoots, viewerMemberId, myReplies),
        owner ? ownerResponse(share, now) : null);
  }

  public PortfolioAnalysisShareDto.CommentResponse commentResponse(
      PortfolioAnalysisComment comment, Long viewerMemberId) {
    return commentResponse(
        comment,
        viewerMemberId,
        comments.findAllByParentIdInOrderByCreatedAtAscIdAsc(List.of(comment.getId())).stream()
            .collect(Collectors.groupingBy(reply -> reply.getParent().getId())));
  }

  private PortfolioAnalysisShareDto.OwnerResponse ownerResponse(
      PortfolioAnalysisShare share,
      Instant now,
      List<PortfolioAnalysisComment> roots,
      Map<Long, List<PortfolioAnalysisComment>> repliesByParent) {
    return new PortfolioAnalysisShareDto.OwnerResponse(
        share.getId(),
        recoverToken(share, now),
        share.getAnalysisId(),
        share.getQuestion(),
        share.getAdditionalExplanation(),
        share.getAdditionalExplanationUpdatedAt(),
        share.status(now),
        share.getExpiresAt(),
        share.getCreatedAt(),
        share.getSnapshot().allAssetsShared(),
        share.getSnapshot().impacts().stream()
            .map(
                impact ->
                    new PortfolioAnalysisShareDto.AssetResponse(
                        impact.assetName(), impact.weight()))
            .toList(),
        commentResponses(roots, share.getOwner().getId(), repliesByParent));
  }

  private List<PortfolioAnalysisShareDto.CommentResponse> commentResponses(
      List<PortfolioAnalysisComment> roots,
      Long viewerMemberId,
      Map<Long, List<PortfolioAnalysisComment>> repliesByParent) {
    return roots.stream()
        .map(comment -> commentResponse(comment, viewerMemberId, repliesByParent))
        .toList();
  }

  private PortfolioAnalysisShareDto.CommentResponse commentResponse(
      PortfolioAnalysisComment comment,
      Long viewerMemberId,
      Map<Long, List<PortfolioAnalysisComment>> repliesByParent) {
    return new PortfolioAnalysisShareDto.CommentResponse(
        comment.getId(),
        comment.getAuthor().getNickname(),
        comment.getContent(),
        comment.getCreatedAt(),
        comment.getUpdatedAt(),
        repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
            .map(
                reply ->
                    new PortfolioAnalysisShareDto.ReplyResponse(
                        reply.getId(),
                        reply.getAuthor().getNickname(),
                        reply.getContent(),
                        reply.getAuthor().getId().equals(comment.getShare().getOwner().getId()),
                        reply.getAuthor().getId().equals(viewerMemberId),
                        reply.getCreatedAt(),
                        reply.getUpdatedAt()))
            .toList());
  }

  private Map<Long, List<PortfolioAnalysisComment>> rootsByShare(
      List<PortfolioAnalysisComment> roots) {
    return roots.stream().collect(Collectors.groupingBy(comment -> comment.getShare().getId()));
  }

  private Map<Long, List<PortfolioAnalysisComment>> repliesByParent(
      List<PortfolioAnalysisComment> roots) {
    if (roots.isEmpty()) return Map.of();
    return comments
        .findAllByParentIdInOrderByCreatedAtAscIdAsc(
            roots.stream().map(PortfolioAnalysisComment::getId).toList())
        .stream()
        .collect(Collectors.groupingBy(reply -> reply.getParent().getId()));
  }

  private List<Long> shareIds(List<PortfolioAnalysisShare> shares) {
    return shares.stream().map(PortfolioAnalysisShare::getId).toList();
  }

  private String recoverToken(PortfolioAnalysisShare share, Instant now) {
    if (share.status(now) != PortfolioAnalysisShare.Status.ACTIVE
        || share.getEncryptedToken() == null) {
      return null;
    }
    try {
      return tokenCipher.decrypt(share.getEncryptedToken());
    } catch (IllegalStateException exception) {
      log.atWarn()
          .setCause(SanitizedLogException.from(exception))
          .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_SHARE_TOKEN_RECOVERY_FAILED.code())
          .addKeyValue("shareId", share.getId())
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log(LogEvent.PORTFOLIO_ANALYSIS_SHARE_TOKEN_RECOVERY_FAILED.message());
      return null;
    }
  }
}
