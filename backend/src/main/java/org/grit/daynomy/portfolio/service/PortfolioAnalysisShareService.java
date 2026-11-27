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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisShareRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
@Service
public class PortfolioAnalysisShareService {

  private static final Duration SHARE_DURATION = Duration.ofDays(7);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PortfolioAnalysisShareRepository shares;
  private final PortfolioAnalysisCommentRepository comments;
  private final PortfolioAnalysisRepository analyses;
  private final PortfolioHoldingRepository holdings;
  private final PortfolioHoldingHistoryRepository holdingHistories;
  private final PortfolioCalculationService calculation;
  private final MemberRepository members;
  private final PortfolioAnalysisShareRateLimiter rateLimiter;
  private final PortfolioShareTokenCipher tokenCipher;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;
  private final PortfolioAnalysisShareSnapshotFactory snapshots;
  private final PortfolioAnalysisShareResponseFactory responses;

  public PortfolioAnalysisShareDto.CreatedResponse create(
      Long memberId, PortfolioAnalysisShareDto.CreateRequest request) {
    var portfolio = prepareShareablePortfolio(memberId, request.analysisId() == null);
    var transaction = new TransactionTemplate(transactionManager);
    return transaction.execute(status -> create(memberId, request, portfolio));
  }

  private PortfolioAnalysisShareDto.CreatedResponse create(
      Long memberId,
      PortfolioAnalysisShareDto.CreateRequest request,
      PreparedShareablePortfolio portfolio) {
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
    Member owner =
        members
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    Instant now = clock.instant();
    String rawToken = newToken();
    ShareablePortfolio shareablePortfolio = shareablePortfolio(analysis == null, portfolio);
    PortfolioAnalysisPublicSnapshot publicSnapshot =
        analysis == null
            ? snapshots.fromPortfolio(
                shareablePortfolio.holdings(), shareablePortfolio.details(), request.assetNames())
            : snapshots.fromAnalysis(
                memberId, analysis, shareablePortfolio.details(), request.assetNames());
    PortfolioAnalysisShare share =
        PortfolioAnalysisShare.create(
            owner,
            analysis == null ? null : analysis.getId(),
            request.question(),
            hash(rawToken),
            tokenCipher.encrypt(rawToken),
            publicSnapshot,
            now.plus(SHARE_DURATION));
    PortfolioAnalysisShare saved = shares.save(share);
    return new PortfolioAnalysisShareDto.CreatedResponse(
        responses.ownerResponse(saved, now), rawToken);
  }

  @Transactional(readOnly = true)
  public List<PortfolioAnalysisShareDto.OwnerResponse> list(Long memberId) {
    Instant now = clock.instant();
    return responses.ownerResponses(
        shares.findAllByOwnerIdOrderByCreatedAtDescIdDesc(memberId), now);
  }

  @Transactional
  public PortfolioAnalysisShareDto.CreatedResponse reissue(Long memberId, Long shareId) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = ownedShare(memberId, shareId);
    Instant now = clock.instant();
    String rawToken = newToken();
    share.reissue(hash(rawToken), tokenCipher.encrypt(rawToken), now.plus(SHARE_DURATION));
    return new PortfolioAnalysisShareDto.CreatedResponse(
        responses.ownerResponse(share, now), rawToken);
  }

  @Transactional
  public PortfolioAnalysisShareDto.OwnerResponse updateExplanation(
      Long memberId, Long shareId, PortfolioAnalysisShareDto.ExplanationRequest request) {
    rateLimiter.acquireWrite(memberId);
    PortfolioAnalysisShare share = ownedShare(memberId, shareId);
    Instant now = clock.instant();
    requireAccessible(share, now);
    share.updateAdditionalExplanation(request.content(), now);
    return responses.ownerResponse(share, now);
  }

  @Transactional
  public PortfolioAnalysisShareDto.OwnerResponse revoke(Long memberId, Long shareId) {
    PortfolioAnalysisShare share = ownedShare(memberId, shareId);
    Instant now = clock.instant();
    share.revoke(now);
    return responses.ownerResponse(share, now);
  }

  @Transactional
  public void delete(Long memberId, Long shareId) {
    shares.delete(ownedShare(memberId, shareId));
  }

  @Transactional(readOnly = true)
  public PortfolioAnalysisShareDto.PublicResponse getOwnerPreview(Long memberId, Long shareId) {
    return responses.publicResponse(ownedShare(memberId, shareId), memberId, clock.instant());
  }

  @Transactional(readOnly = true)
  public PortfolioAnalysisShareDto.PublicResponse getPublic(
      String rawToken, Long viewerMemberId, String remoteAddress) {
    rateLimiter.acquireRead(remoteAddress);
    PortfolioAnalysisShare share = requirePublicAccess(rawToken);
    return responses.publicResponse(share, viewerMemberId, clock.instant());
  }

  PortfolioAnalysisShare requirePublicAccessForUpdate(String rawToken) {
    PortfolioAnalysisShare share = findPublicForUpdate(rawToken);
    requireAccessible(share, clock.instant());
    return share;
  }

  PortfolioAnalysisShare findPublicForUpdate(String rawToken) {
    return shares
        .findByTokenHashForUpdate(hash(rawToken))
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
  }

  private PortfolioAnalysisShare requirePublicAccess(String rawToken) {
    PortfolioAnalysisShare share =
        shares
            .findByTokenHash(hash(rawToken))
            .orElseThrow(
                () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    requireAccessible(share, clock.instant());
    return share;
  }

  private PortfolioAnalysisShare ownedShare(Long memberId, Long shareId) {
    return shares
        .findByIdAndOwnerId(shareId, memberId)
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public PortfolioAnalysisShare requireOwner(Long memberId, Long shareId) {
    return ownedShare(memberId, shareId);
  }

  @Transactional(readOnly = true)
  public PortfolioAnalysisShare requireOwnerAccess(Long memberId, Long shareId) {
    PortfolioAnalysisShare share = ownedShare(memberId, shareId);
    requireAccessible(share, clock.instant());
    return share;
  }

  @Transactional(readOnly = true)
  public void requireSimulationAccess(Long memberId, Long shareId, Long commentId) {
    requireOwnerAccess(memberId, shareId);
    comments
        .findByIdAndShareId(commentId, shareId)
        .filter(comment -> !comment.isReply())
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
  }

  public List<PortfolioAnalysisShareDto.SimulationAssetResponse> simulationAssets(
      Long memberId, Long shareId, Long commentId) {
    readTransaction()
        .executeWithoutResult(status -> requireSimulationAccess(memberId, shareId, commentId));
    var portfolio = prepareShareablePortfolio(memberId, false);
    return readTransaction()
        .execute(status -> simulationAssets(memberId, shareId, commentId, portfolio));
  }

  private List<PortfolioAnalysisShareDto.SimulationAssetResponse> simulationAssets(
      Long memberId, Long shareId, Long commentId, PreparedShareablePortfolio preparedPortfolio) {
    requireSimulationAccess(memberId, shareId, commentId);
    var portfolio = shareablePortfolio(false, preparedPortfolio);
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

  private PreparedShareablePortfolio prepareShareablePortfolio(Long memberId, boolean required) {
    record HoldingsSnapshot(Long portfolioId, PortfolioCalculateRequest request) {}
    var snapshot =
        readTransaction()
            .execute(
                status -> {
                  var savedHoldings = holdings.findAllByPortfolioMemberId(memberId);
                  if (savedHoldings.isEmpty() && required) {
                    throw new BusinessException(
                        PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
                  }
                  return new HoldingsSnapshot(
                      savedHoldings.isEmpty()
                          ? null
                          : savedHoldings.getFirst().getPortfolio().getId(),
                      calculationRequest(savedHoldings));
                });
    return new PreparedShareablePortfolio(
        snapshot.portfolioId(), snapshot.request(), calculation.preparePrices(snapshot.request()));
  }

  private ShareablePortfolio shareablePortfolio(
      boolean required, PreparedShareablePortfolio portfolio) {
    if (portfolio.request().holdings().isEmpty()) {
      if (required) {
        throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
      }
      return new ShareablePortfolio(List.of(), Map.of());
    }
    var calculated = calculation.calculate(portfolio.request(), portfolio.prices());
    Map<Long, LocalDate> firstPurchasedOn = firstPurchasedOn(portfolio.portfolioId());
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

  private PortfolioCalculateRequest calculationRequest(List<PortfolioHolding> savedHoldings) {
    return new PortfolioCalculateRequest(
        savedHoldings.stream()
            .map(
                holding ->
                    new PortfolioHoldingRequest(
                        holding.getAsset().getId(),
                        holding.getQuantity(),
                        holding.getAveragePurchasePrice()))
            .toList());
  }

  private TransactionTemplate readTransaction() {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);
    return transaction;
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

  private record PreparedShareablePortfolio(
      Long portfolioId,
      PortfolioCalculateRequest request,
      PortfolioCalculationService.PreparedPrices prices) {}

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
