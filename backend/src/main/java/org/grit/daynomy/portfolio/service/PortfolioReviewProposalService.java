package org.grit.daynomy.portfolio.service;

import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposal;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposalSnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioReviewProposalDto;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.grit.daynomy.portfolio.repository.PortfolioReviewProposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioReviewProposalService {

  private final PortfolioAnalysisShareService shares;
  private final PortfolioAnalysisCommentRepository comments;
  private final PortfolioReviewProposalRepository proposals;
  private final AssetRepository assets;
  private final PortfolioAnalysisShareRateLimiter rateLimiter;

  @Transactional(readOnly = true)
  public List<PortfolioReviewProposalDto.Response> list(Long memberId, Long shareId) {
    shares.requireOwnerAccess(memberId, shareId);
    return proposals.findAllByShareIdOrderByUpdatedAtDescIdDesc(shareId).stream()
        .map(PortfolioReviewProposalDto.Response::from)
        .toList();
  }

  @Transactional
  public PortfolioReviewProposalDto.Response save(
      Long memberId, Long shareId, Long commentId, PortfolioReviewProposalDto.Request request) {
    rateLimiter.acquireWrite(memberId);
    var share = shares.requireOwnerAccess(memberId, shareId);
    PortfolioAnalysisComment comment = rootCommentForUpdate(shareId, commentId);
    PortfolioReviewProposalSnapshot snapshot = snapshot(request);
    PortfolioReviewProposal proposal =
        proposals
            .findByCommentId(commentId)
            .map(
                existing -> {
                  existing.update(snapshot);
                  return existing;
                })
            .orElseGet(() -> PortfolioReviewProposal.create(share, comment, snapshot));
    return PortfolioReviewProposalDto.Response.from(proposals.saveAndFlush(proposal));
  }

  @Transactional
  public void delete(Long memberId, Long shareId, Long commentId) {
    rateLimiter.acquireWrite(memberId);
    shares.requireOwnerAccess(memberId, shareId);
    rootCommentForUpdate(shareId, commentId);
    proposals.findByCommentId(commentId).ifPresent(proposals::delete);
  }

  private PortfolioAnalysisComment rootCommentForUpdate(Long shareId, Long commentId) {
    return comments
        .findByIdAndShareIdForUpdate(commentId, shareId)
        .filter(comment -> !comment.isReply())
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
  }

  private PortfolioReviewProposalSnapshot snapshot(PortfolioReviewProposalDto.Request request) {
    var requestedAssets = request.assets();
    var assetIds =
        requestedAssets.stream().map(PortfolioReviewProposalDto.AssetRequest::assetId).toList();
    if (assetIds.stream().distinct().count() != assetIds.size()) {
      throw invalidAssets();
    }
    var assetsById = new HashMap<Long, Asset>();
    assets.findAllById(assetIds).forEach(asset -> assetsById.put(asset.getId(), asset));
    if (assetsById.size() != assetIds.size()) {
      throw invalidAssets();
    }
    try {
      return new PortfolioReviewProposalSnapshot(
          requestedAssets.stream()
              .map(
                  requestAsset -> {
                    Asset asset = assetsById.get(requestAsset.assetId());
                    if (asset.getCategory() != AssetCategory.STOCK
                        && asset.getCategory() != AssetCategory.ETF) {
                      throw invalidAssets();
                    }
                    return new PortfolioReviewProposalSnapshot.Asset(
                        asset.getId(),
                        asset.getDisplayName(),
                        asset.getCategory(),
                        requestAsset.weight());
                  })
              .toList());
    } catch (IllegalArgumentException exception) {
      throw invalidAssets();
    }
  }

  private BusinessException invalidAssets() {
    return new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS);
  }
}
