package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCommentRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisShareRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PortfolioReviewSimulationServiceTest {
  private final PortfolioAnalysisShareRepository shares =
      mock(PortfolioAnalysisShareRepository.class);
  private final PortfolioAnalysisCommentRepository comments =
      mock(PortfolioAnalysisCommentRepository.class);
  private final PortfolioAnalysisShareRateLimiter limiter =
      mock(PortfolioAnalysisShareRateLimiter.class);
  private final PortfolioAnalysisService analysis = mock(PortfolioAnalysisService.class);
  private final PlatformTransactionManager transactions = transactionManager();
  private final Instant now = Instant.parse("2026-11-14T00:00:00Z");
  private final PortfolioAnalysisShareService access =
      new PortfolioAnalysisShareService(
          shares,
          comments,
          null,
          null,
          null,
          null,
          null,
          limiter,
          mock(PortfolioShareTokenCipher.class),
          Clock.fixed(now, ZoneOffset.UTC),
          transactions,
          mock(PortfolioAnalysisShareSnapshotFactory.class),
          mock(PortfolioAnalysisShareResponseFactory.class));
  private final PortfolioReviewSimulationService service =
      new PortfolioReviewSimulationService(access, analysis, limiter);

  private PortfolioAnalysisShare allow() {
    var share = mock(PortfolioAnalysisShare.class);
    when(shares.findByIdAndOwnerId(3L, 1L)).thenReturn(Optional.of(share));
    when(share.status(now)).thenReturn(PortfolioAnalysisShare.Status.ACTIVE);
    when(comments.findByIdAndShareId(9L, 3L))
        .thenReturn(Optional.of(mock(PortfolioAnalysisComment.class)));
    return share;
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345.67", "0", "-1"})
  void ownerReceivesOnlyUsableEvaluationAmounts(String amount) {
    allow();
    var holdings = mock(PortfolioHoldingRepository.class);
    var histories = mock(PortfolioHoldingHistoryRepository.class);
    var calculation = mock(PortfolioCalculationService.class);
    var holding = mock(PortfolioHolding.class);
    var portfolio = mock(Portfolio.class);
    var asset = mock(Asset.class);
    when(holding.getAsset()).thenReturn(asset);
    when(asset.getId()).thenReturn(10L);
    when(holding.getPortfolio()).thenReturn(portfolio);
    when(portfolio.getId()).thenReturn(20L);
    when(holding.getQuantity()).thenReturn(1L);
    when(holding.getAveragePurchasePrice()).thenReturn(BigDecimal.ONE);
    when(holdings.findAllByPortfolioMemberId(1L)).thenReturn(List.of(holding));
    var response = mock(PortfolioHoldingResponse.class);
    when(response.assetId()).thenReturn(10L);
    when(response.name()).thenReturn("삼성전자");
    when(response.category()).thenReturn(AssetCategory.STOCK);
    when(response.priceAvailable()).thenReturn(true);
    when(response.evaluationAmount()).thenReturn(new BigDecimal(amount));
    var calculated = mock(PortfolioCalculationResponse.class);
    when(calculated.holdings()).thenReturn(List.of(response));
    var prepared =
        new PortfolioCalculationService.PreparedPrices(Map.of(), Map.of(), System.nanoTime());
    when(calculation.preparePrices(any())).thenReturn(prepared);
    when(calculation.calculate(any(), eq(prepared))).thenReturn(calculated);
    var snapshots =
        new PortfolioAnalysisShareService(
            shares,
            comments,
            null,
            holdings,
            histories,
            calculation,
            null,
            limiter,
            mock(PortfolioShareTokenCipher.class),
            Clock.fixed(now, ZoneOffset.UTC),
            transactions,
            mock(PortfolioAnalysisShareSnapshotFactory.class),
            mock(PortfolioAnalysisShareResponseFactory.class));
    if (new BigDecimal(amount).signum() <= 0) {
      assertThatThrownBy(() -> snapshots.simulationAssets(1L, 3L, 9L))
          .isInstanceOf(BusinessException.class);
    } else {
      var result = snapshots.simulationAssets(1L, 3L, 9L);
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().amount()).isEqualByComparingTo(amount);
      assertThat(result.getFirst().category()).isEqualTo(AssetCategory.STOCK);
      when(response.priceAvailable()).thenReturn(false);
      assertThatThrownBy(() -> snapshots.simulationAssets(1L, 3L, 9L))
          .isInstanceOf(BusinessException.class);
      when(response.priceAvailable()).thenReturn(true);
      when(response.evaluationAmount()).thenReturn(null);
      assertThatThrownBy(() -> snapshots.simulationAssets(1L, 3L, 9L))
          .isInstanceOf(BusinessException.class);
    }
    int calculationCount = new BigDecimal(amount).signum() <= 0 ? 1 : 3;
    var preparedRequests = ArgumentCaptor.forClass(PortfolioCalculateRequest.class);
    var calculatedRequests = ArgumentCaptor.forClass(PortfolioCalculateRequest.class);
    verify(calculation, times(calculationCount)).preparePrices(preparedRequests.capture());
    verify(calculation, times(calculationCount))
        .calculate(calculatedRequests.capture(), eq(prepared));
    for (int index = 0; index < calculationCount; index++) {
      assertThat(calculatedRequests.getAllValues().get(index))
          .isSameAs(preparedRequests.getAllValues().get(index));
    }
    verify(holdings, times(calculationCount)).findAllByPortfolioMemberId(1L);
    verify(holdings, never()).save(any());
    verifyNoInteractions(analysis);
  }

  @Test
  void sendsOnlyTemporaryAssetsWithoutSavingHoldingsOrAnalysis() {
    allow();
    var request =
        new PortfolioAnalysisRequest(
            List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal("100"), 99L)));
    service.analyze(1L, 3L, 9L, request);
    var captured = ArgumentCaptor.forClass(PortfolioAnalysisRequest.class);
    verify(analysis).analyze(captured.capture(), eq(true));
    assertThat(captured.getValue().assets())
        .containsExactly(new PortfolioAssetRequest("삼성전자", new BigDecimal("100")));
    assertThat(request.assets().getFirst().quantity()).isEqualTo(99L);
    verify(shares, never()).save(any());
    verify(comments, never()).save(any());
  }

  @Test
  void otherMemberCannotReadTheSnapshotOrStartAnalysis() {
    assertThatThrownBy(() -> access.simulationAssets(2L, 3L, 9L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    assertThatThrownBy(() -> service.analyze(2L, 3L, 9L, validRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND));
    verifyNoInteractions(analysis);
  }

  @Test
  void commentFromAnotherShareCannotBeUsed() {
    allow();
    when(comments.findByIdAndShareId(9L, 3L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.analyze(1L, 3L, 9L, validRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND));
    verifyNoInteractions(analysis);
  }

  @Test
  void expiredShareCannotBeUsed() {
    var share = allow();
    when(share.status(now)).thenReturn(PortfolioAnalysisShare.Status.EXPIRED);
    assertThatThrownBy(() -> service.analyze(1L, 3L, 9L, validRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_EXPIRED));
    verifyNoInteractions(analysis);
  }

  @ParameterizedTest
  @ValueSource(strings = {"90", "110"})
  void existingAnalysisValidationRejectsInvalidTotal(String weight) {
    allow();
    when(analysis.analyze(any(), eq(true))).thenCallRealMethod();
    doCallRealMethod().when(analysis).validate(any());
    assertThatThrownBy(
            () ->
                service.analyze(
                    1L,
                    3L,
                    9L,
                    new PortfolioAnalysisRequest(
                        List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal(weight))))))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void emptySnapshotDoesNotCallAnalysis() {
    allow();
    assertThatThrownBy(() -> service.analyze(1L, 3L, 9L, new PortfolioAnalysisRequest(List.of())))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(analysis);
  }

  private PortfolioAnalysisRequest validRequest() {
    return new PortfolioAnalysisRequest(
        List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal("100"))));
  }

  private static PlatformTransactionManager transactionManager() {
    var transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return transactions;
  }
}
