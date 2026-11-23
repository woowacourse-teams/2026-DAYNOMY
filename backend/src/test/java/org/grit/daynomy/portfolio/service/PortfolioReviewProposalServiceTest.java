package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposal;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposalSnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioReviewProposalDto;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.grit.daynomy.portfolio.repository.PortfolioReviewProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioReviewProposalServiceTest {

  private final PortfolioAnalysisShareService shares = mock(PortfolioAnalysisShareService.class);
  private final PortfolioAnalysisCommentRepository comments =
      mock(PortfolioAnalysisCommentRepository.class);
  private final PortfolioReviewProposalRepository proposals =
      mock(PortfolioReviewProposalRepository.class);
  private final AssetRepository assets = mock(AssetRepository.class);
  private final PortfolioAnalysisShareRateLimiter rateLimiter =
      mock(PortfolioAnalysisShareRateLimiter.class);
  private final PortfolioReviewProposalService service =
      new PortfolioReviewProposalService(shares, comments, proposals, assets, rateLimiter);
  private final PortfolioAnalysisShare share = mock(PortfolioAnalysisShare.class);
  private final PortfolioAnalysisComment comment = mock(PortfolioAnalysisComment.class);

  @BeforeEach
  void setUp() {
    when(share.getId()).thenReturn(3L);
    when(comment.getId()).thenReturn(9L);
    when(comment.getShare()).thenReturn(share);
    when(comment.isReply()).thenReturn(false);
    when(shares.requireOwnerAccess(1L, 3L)).thenReturn(share);
    when(comments.findByIdAndShareIdForUpdate(9L, 3L)).thenReturn(Optional.of(comment));
    when(proposals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void savesServerOwnedAssetSnapshotForRootComment() {
    Asset samsung = asset(10L, "삼성전자", AssetCategory.STOCK);
    when(assets.findAllById(List.of(10L))).thenReturn(List.of(samsung));
    when(proposals.findByCommentId(9L)).thenReturn(Optional.empty());

    var response = service.save(1L, 3L, 9L, request(assetRequest(10L, "100")));

    var captured = ArgumentCaptor.forClass(PortfolioReviewProposal.class);
    verify(proposals).saveAndFlush(captured.capture());
    assertThat(captured.getValue().getSnapshot().assets())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.assetId()).isEqualTo(10L);
              assertThat(saved.assetName()).isEqualTo("삼성전자");
              assertThat(saved.category()).isEqualTo(AssetCategory.STOCK);
              assertThat(saved.weight()).isEqualByComparingTo("100");
            });
    assertThat(response.shareId()).isEqualTo(3L);
    assertThat(response.commentId()).isEqualTo(9L);
  }

  @Test
  void updatesExistingProposalInsteadOfCreatingAnotherOne() {
    Asset samsung = asset(10L, "삼성전자", AssetCategory.STOCK);
    Asset etf = asset(20L, "KODEX 200", AssetCategory.ETF);
    var existing =
        PortfolioReviewProposal.create(
            share,
            comment,
            new PortfolioReviewProposalSnapshot(
                List.of(
                    new PortfolioReviewProposalSnapshot.Asset(
                        10L, "삼성전자", AssetCategory.STOCK, new BigDecimal("100")))));
    when(assets.findAllById(List.of(10L, 20L))).thenReturn(List.of(samsung, etf));
    when(proposals.findByCommentId(9L)).thenReturn(Optional.of(existing));

    service.save(
        1L,
        3L,
        9L,
        request(assetRequest(10L, "60"), assetRequest(20L, "40")));

    verify(proposals).saveAndFlush(existing);
    assertThat(existing.getSnapshot().assets())
        .extracting(PortfolioReviewProposalSnapshot.Asset::weight)
        .containsExactly(new BigDecimal("60"), new BigDecimal("40"));
  }

  @Test
  void rejectsMissingAssetsBeforeSaving() {
    when(assets.findAllById(List.of(10L))).thenReturn(List.of());

    assertThatThrownBy(() -> service.save(1L, 3L, 9L, request(assetRequest(10L, "100"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS));
    verify(proposals, never()).saveAndFlush(any());
  }

  @Test
  void deletesExistingProposalAndTreatsMissingProposalAsAlreadyDeleted() {
    var existing =
        PortfolioReviewProposal.create(
            share,
            comment,
            new PortfolioReviewProposalSnapshot(
                List.of(
                    new PortfolioReviewProposalSnapshot.Asset(
                        10L, "삼성전자", AssetCategory.STOCK, new BigDecimal("100")))));
    when(proposals.findByCommentId(9L)).thenReturn(Optional.of(existing), Optional.empty());

    service.delete(1L, 3L, 9L);
    service.delete(1L, 3L, 9L);

    verify(proposals).delete(existing);
  }

  private Asset asset(Long id, String name, AssetCategory category) {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(id);
    when(asset.getDisplayName()).thenReturn(name);
    when(asset.getCategory()).thenReturn(category);
    return asset;
  }

  private PortfolioReviewProposalDto.AssetRequest assetRequest(Long id, String weight) {
    return new PortfolioReviewProposalDto.AssetRequest(id, new BigDecimal(weight));
  }

  private PortfolioReviewProposalDto.Request request(
      PortfolioReviewProposalDto.AssetRequest... assets) {
    return new PortfolioReviewProposalDto.Request(List.of(assets));
  }
}
