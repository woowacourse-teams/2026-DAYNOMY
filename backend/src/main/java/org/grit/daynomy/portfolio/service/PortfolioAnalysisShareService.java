package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisShareRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioAnalysisShareService {

  private static final Duration SHARE_DURATION = Duration.ofDays(7);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PortfolioAnalysisShareRepository shares;
  private final PortfolioAnalysisCommentRepository comments;
  private final PortfolioAnalysisRepository analyses;
  private final PortfolioAnalysisEvidenceRepository evidence;
  private final PortfolioHoldingRepository holdings;
  private final PortfolioHoldingHistoryRepository holdingHistories;
  private final PortfolioCalculationService calculation;
  private final MemberRepository members;
  private final PortfolioAnalysisShareRateLimiter rateLimiter;
  private final Clock clock;

  @Transactional
  public PortfolioAnalysisShareDto.CreatedResponse create(
      Long memberId, PortfolioAnalysisShareDto.CreateRequest request) {
    PortfolioAnalysis analysis =
        request.analysisId() == null
            ? null
            : analyses
                .findByIdAndPortfolioMemberId(request.analysisId(), memberId)
                .orElseThrow(
                    () ->
                        new BusinessException(
                            PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    if (analysis != null && analysis.getSummaryStatus() != PortfolioAnalysis.SummaryStatus.READY) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_READY);
    }
    Member owner = members.getReferenceById(memberId);
    Instant now = clock.instant();
    String rawToken = newToken();
    ShareablePortfolio portfolio = shareablePortfolio(memberId, analysis == null);
    PortfolioAnalysisPublicSnapshot publicSnapshot =
        analysis == null
            ? portfolioSnapshot(portfolio, request.assetNames())
            : publicSnapshot(memberId, analysis, portfolio, request.assetNames());
    Optional<PortfolioAnalysisShare> existingShare =
        analysis == null
            ? shares.findFirstByOwnerIdAndAnalysisIdIsNullOrderByCreatedAtDesc(memberId)
            : shares.findByOwnerIdAndAnalysisId(memberId, analysis.getId());
    PortfolioAnalysisShare share =
        existingShare
            .map(
                existing -> {
                  existing.renew(
                      request.question(), hash(rawToken), publicSnapshot, now.plus(SHARE_DURATION));
                  return existing;
                })
            .orElseGet(
                () ->
                    PortfolioAnalysisShare.create(
                        owner,
                        analysis == null ? null : analysis.getId(),
                        request.question(),
                        hash(rawToken),
                        publicSnapshot,
                        now.plus(SHARE_DURATION)));
    PortfolioAnalysisShare saved = shares.save(share);
    return new PortfolioAnalysisShareDto.CreatedResponse(ownerResponse(saved, now), rawToken);
  }

  @Transactional(readOnly = true)
  public List<PortfolioAnalysisShareDto.OwnerResponse> list(Long memberId) {
    Instant now = clock.instant();
    return shares.findAllByOwnerIdOrderByCreatedAtDescIdDesc(memberId).stream()
        .map(share -> ownerResponse(share, now))
        .toList();
  }

  @Transactional
  public PortfolioAnalysisShareDto.OwnerResponse revoke(Long memberId, Long shareId) {
    PortfolioAnalysisShare share = ownedShare(memberId, shareId);
    Instant now = clock.instant();
    share.revoke(now);
    return ownerResponse(share, now);
  }

  @Transactional
  public void delete(Long memberId, Long shareId) {
    shares.delete(ownedShare(memberId, shareId));
  }

  @Transactional(readOnly = true)
  public PortfolioAnalysisShareDto.PublicResponse getPublic(
      String rawToken, Long viewerMemberId, String remoteAddress) {
    rateLimiter.acquireRead(remoteAddress);
    PortfolioAnalysisShare share =
        shares
            .findByTokenHash(hash(rawToken))
            .orElseThrow(
                () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    requireAccessible(share, clock.instant());
    List<PortfolioAnalysisShareDto.CommentResponse> myComments =
        viewerMemberId == null
            ? List.of()
            : comments
                .findAllByShareIdAndAuthorIdAndParentIsNullOrderByCreatedAtAscIdAsc(
                    share.getId(), viewerMemberId)
                .stream()
                .map(comment -> commentResponse(comment, viewerMemberId))
                .toList();
    return new PortfolioAnalysisShareDto.PublicResponse(
        share.getOwner().getNickname(),
        share.getQuestion(),
        share.getExpiresAt(),
        PortfolioAnalysisShareDto.SnapshotResponse.from(share.getSnapshot()),
        viewerMemberId != null && !share.getOwner().getId().equals(viewerMemberId),
        myComments,
        share.getOwner().getId().equals(viewerMemberId)
            ? ownerResponse(share, clock.instant())
            : null);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse createComment(
      String rawToken, Long memberId, PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share =
        shares
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(
                () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    requireAccessible(share, clock.instant());
    if (share.getOwner().getId().equals(memberId)) {
      throw new BusinessException(
          PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_OWNER_COMMENT_FORBIDDEN);
    }
    PortfolioAnalysisComment comment =
        PortfolioAnalysisComment.root(share, members.getReferenceById(memberId), request.content());
    return commentResponse(comments.save(comment), memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse updateComment(
      String rawToken,
      Long memberId,
      Long commentId,
      PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share =
        shares
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(
                () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    requireAccessible(share, clock.instant());
    PortfolioAnalysisComment comment = ownedRootComment(memberId, commentId, share.getId());
    comment.update(request.content());
    return commentResponse(comment, memberId);
  }

  @Transactional
  public void deleteComment(String rawToken, Long memberId, Long commentId) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share =
        shares
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(
                () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    PortfolioAnalysisComment comment = ownedRootComment(memberId, commentId, share.getId());
    comments.delete(comment);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse saveOwnerReply(
      Long memberId,
      Long shareId,
      Long commentId,
      PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = ownedShare(memberId, shareId);
    requireAccessible(share, clock.instant());
    PortfolioAnalysisComment comment =
        comments
            .findByIdAndShareId(commentId, shareId)
            .filter(candidate -> !candidate.isReply())
            .orElseThrow(
                () ->
                    new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
    comments.save(
        PortfolioAnalysisComment.reply(
            comment, members.getReferenceById(memberId), request.content()));
    return commentResponse(comment, memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse createCommenterReply(
      String rawToken,
      Long memberId,
      Long commentId,
      PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share =
        shares
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(
                () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    requireAccessible(share, clock.instant());
    PortfolioAnalysisComment comment =
        comments
            .findByIdAndShareId(commentId, share.getId())
            .filter(candidate -> !candidate.isReply())
            .filter(candidate -> candidate.getAuthor().getId().equals(memberId))
            .orElseThrow(
                () ->
                    new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
    comments.save(
        PortfolioAnalysisComment.reply(
            comment, members.getReferenceById(memberId), request.content()));
    return commentResponse(comment, memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse updateReply(
      Long memberId, Long replyId, PortfolioAnalysisShareDto.CommentRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisComment reply = ownedReply(memberId, replyId);
    reply.update(request.content());
    return commentResponse(reply.getParent(), memberId);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CommentResponse deleteReply(Long memberId, Long replyId) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisComment reply = ownedReply(memberId, replyId);
    PortfolioAnalysisComment parent = reply.getParent();
    comments.delete(reply);
    comments.flush();
    return commentResponse(parent, memberId);
  }

  private PortfolioAnalysisShare ownedShare(Long memberId, Long shareId) {
    return shares
        .findByIdAndOwnerId(shareId, memberId)
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public void requireSimulationAccess(Long memberId, Long shareId, Long commentId) {
    requireAccessible(ownedShare(memberId, shareId), clock.instant());
    comments
        .findByIdAndShareId(commentId, shareId)
        .filter(comment -> !comment.isReply())
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public List<PortfolioAnalysisShareDto.SimulationAssetResponse> simulationAssets(
      Long memberId, Long shareId, Long commentId) {
    requireSimulationAccess(memberId, shareId, commentId);
    var portfolio = shareablePortfolio(memberId, false);
    if (portfolio.holdings().isEmpty()
        || portfolio.holdings().stream()
            .anyMatch(
                holding ->
                    !holding.priceAvailable()
                        || holding.evaluationAmount() == null
                        || holding.evaluationAmount().signum() <= 0)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE);
    }
    return portfolio.holdings().stream()
        .map(
            holding ->
                new PortfolioAnalysisShareDto.SimulationAssetResponse(
                    holding.assetId(),
                    holding.name(),
                    holding.category(),
                    holding.evaluationAmount()))
        .toList();
  }

  private PortfolioAnalysisComment ownedRootComment(Long memberId, Long commentId, Long shareId) {
    return comments
        .findByIdAndAuthorId(commentId, memberId)
        .filter(comment -> !comment.isReply())
        .filter(comment -> comment.getShare().getId().equals(shareId))
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
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

  private PortfolioAnalysisShareDto.OwnerResponse ownerResponse(
      PortfolioAnalysisShare share, Instant now) {
    List<PortfolioAnalysisShareDto.CommentResponse> commentResponses =
        comments.findAllByShareIdAndParentIsNullOrderByCreatedAtAscIdAsc(share.getId()).stream()
            .map(comment -> commentResponse(comment, share.getOwner().getId()))
            .toList();
    return new PortfolioAnalysisShareDto.OwnerResponse(
        share.getId(),
        share.getAnalysisId(),
        share.getQuestion(),
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
        commentResponses);
  }

  private PortfolioAnalysisShareDto.CommentResponse commentResponse(
      PortfolioAnalysisComment comment, Long viewerMemberId) {
    return new PortfolioAnalysisShareDto.CommentResponse(
        comment.getId(),
        comment.getAuthor().getNickname(),
        comment.getContent(),
        comment.getCreatedAt(),
        comment.getUpdatedAt(),
        comments.findAllByParentIdOrderByCreatedAtAscIdAsc(comment.getId()).stream()
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

  private PortfolioAnalysisPublicSnapshot publicSnapshot(
      Long memberId,
      PortfolioAnalysis analysis,
      ShareablePortfolio portfolio,
      List<String> requestedAssetNames) {
    PortfolioAnalysisResponse response =
        PortfolioAnalysisResponse.from(
            analysis,
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                analysis.getId(), memberId));
    LinkedHashSet<String> assetNames = new LinkedHashSet<>();
    for (String assetName : requestedAssetNames) assetNames.add(assetName.strip());
    List<PortfolioAssetImpactResponse> selectedImpacts =
        response.impacts().stream()
            .filter(impact -> assetNames.contains(impact.assetName()))
            .toList();
    if (assetNames.size() != requestedAssetNames.size()
        || selectedImpacts.size() != assetNames.size()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
    }
    boolean allAssetsShared =
        selectedImpacts.size() == response.impacts().size() && response.excludedAssets().isEmpty();
    return new PortfolioAnalysisPublicSnapshot(
        selectedImpacts.size(),
        selectedImpacts.size(),
        allAssetsShared,
        true,
        allAssetsShared ? response.overallDirection() : null,
        allAssetsShared ? response.overallScore() : null,
        allAssetsShared ? response.positiveImpactScore() : BigDecimal.ZERO,
        allAssetsShared ? response.negativeImpactScore() : BigDecimal.ZERO,
        response.analyzedAt(),
        allAssetsShared ? response.overallImpact() : "",
        selectedImpacts.stream()
            .map(
                impact ->
                    new PortfolioAnalysisPublicSnapshot.AssetImpact(
                        impact.assetName(),
                        impact.weight(),
                        impact.direction(),
                        impact.impactLevel(),
                        impact.issueSummary(),
                        impact.expectedReaction(),
                        impact.outlook(),
                        impact.reason(),
                        impact.evidenceSentence(),
                        impact.sources().stream()
                            .map(
                                source ->
                                    new PortfolioAnalysisPublicSnapshot.Source(
                                        source.title(),
                                        source.url(),
                                        source.publishedOn(),
                                        source.occurredOn(),
                                        source.excerpt(),
                                        source.factQuote()))
                            .toList(),
                        portfolio.details().get(impact.assetName()),
                        impact.rank()))
            .toList(),
        selectedImpacts.stream()
            .flatMap(impact -> impact.sources().stream())
            .distinct()
            .map(
                source ->
                    new PortfolioAnalysisPublicSnapshot.Source(
                        source.title(),
                        source.url(),
                        source.publishedOn(),
                        source.occurredOn(),
                        source.excerpt(),
                        source.factQuote()))
            .toList(),
        List.of());
  }

  private PortfolioAnalysisPublicSnapshot portfolioSnapshot(
      ShareablePortfolio portfolio, List<String> requestedAssetNames) {
    LinkedHashSet<String> assetNames = new LinkedHashSet<>();
    requestedAssetNames.forEach(assetName -> assetNames.add(assetName.strip()));
    var available =
        portfolio.holdings().stream()
            .filter(holding -> holding.priceAvailable() && holding.weight() != null)
            .toList();
    var selected =
        available.stream().filter(holding -> assetNames.contains(holding.name())).toList();
    if (assetNames.size() != requestedAssetNames.size() || selected.size() != assetNames.size()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
    }
    var impacts =
        IntStream.range(0, selected.size())
            .mapToObj(
                index -> {
                  var holding = selected.get(index);
                  return new PortfolioAnalysisPublicSnapshot.AssetImpact(
                      holding.name(),
                      holding.weight(),
                      null,
                      null,
                      "",
                      "",
                      "",
                      "",
                      "",
                      List.of(),
                      portfolio.details().get(holding.name()),
                      index + 1);
                })
            .toList();
    return new PortfolioAnalysisPublicSnapshot(
        impacts.size(),
        0,
        impacts.size() == available.size(),
        false,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        "",
        impacts,
        List.of(),
        List.of());
  }

  private ShareablePortfolio shareablePortfolio(Long memberId, boolean required) {
    var savedHoldings = holdings.findAllByPortfolioMemberId(memberId);
    if (savedHoldings.isEmpty()) {
      if (required) {
        throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
      }
      return new ShareablePortfolio(List.of(), Map.of());
    }
    var calculated =
        calculation.calculate(
            new PortfolioCalculateRequest(
                savedHoldings.stream()
                    .map(
                        holding ->
                            new PortfolioHoldingRequest(
                                holding.getAsset().getId(),
                                holding.getQuantity(),
                                holding.getAveragePurchasePrice()))
                    .toList()));
    Map<Long, LocalDate> firstPurchasedOn =
        firstPurchasedOn(savedHoldings.getFirst().getPortfolio().getId());
    Map<String, PortfolioAnalysisPublicSnapshot.HoldingContext> details = new HashMap<>();
    for (PortfolioHoldingResponse holding : calculated.holdings()) {
      BigDecimal currentPrice =
          holding.delayedPrice() == null ? holding.closePrice() : holding.delayedPrice();
      details.put(
          holding.name(),
          new PortfolioAnalysisPublicSnapshot.HoldingContext(
              holding.assetCode(),
              holding.market() == null ? null : holding.market().name(),
              firstPurchasedOn.get(holding.assetId()),
              holding.averagePurchasePrice(),
              currentPrice,
              holding.currency(),
              holding.baseDate(),
              holding.delayedPriceAsOf(),
              holding.returnRate()));
    }
    return new ShareablePortfolio(calculated.holdings(), Map.copyOf(details));
  }

  private Map<Long, LocalDate> firstPurchasedOn(Long portfolioId) {
    Map<Long, Long> quantities = new HashMap<>();
    Map<Long, LocalDate> startedOn = new HashMap<>();
    for (var history :
        holdingHistories.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
            portfolioId)) {
      Long assetId = history.getAsset().getId();
      long before = quantities.getOrDefault(assetId, 0L);
      long after =
          history.getChangeType() == PortfolioHoldingChangeType.REMOVED ? 0 : history.getQuantity();
      if (before <= 0 && after > 0) {
        if (history.getChangeType() == PortfolioHoldingChangeType.BUY) {
          startedOn.put(assetId, history.getEffectiveDate());
        } else {
          startedOn.remove(assetId);
        }
      } else if (after <= 0) {
        startedOn.remove(assetId);
      }
      quantities.put(assetId, after);
    }
    return startedOn;
  }

  private record ShareablePortfolio(
      List<PortfolioHoldingResponse> holdings,
      Map<String, PortfolioAnalysisPublicSnapshot.HoldingContext> details) {}

  private static void requireAccessible(PortfolioAnalysisShare share, Instant now) {
    switch (share.status(now)) {
      case ACTIVE -> {}
      case EXPIRED ->
          throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_EXPIRED);
      case REVOKED ->
          throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_REVOKED);
    }
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String value) {
    if (value == null || value.length() > 100) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND);
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
    }
  }
}
