package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.LeagueTypes.HoldingPeriod;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.PortfolioTransaction;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.PortfolioTransactionRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioTransactionServiceTest {
  @Mock private PortfolioTransactionRepository transactionRepository;
  @Mock private InvestmentReviewRepository reviewRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private MemberRepository memberRepository;
  @InjectMocks private PortfolioTransactionService service;

  @Test
  void holdingDecisionSnapshotsPositionWithoutChangingQuantityPriceOrVisibility() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    SharedHolding holding = new SharedHolding(portfolio, asset(), 2, new BigDecimal("100.00"));
    holding.describe(true, "기존 근거");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(null, 2L)).willReturn(Optional.of(holding));
    given(transactionRepository.save(any(PortfolioTransaction.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    var response =
        service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision()));
    assertThat(response.transactionType()).isEqualTo(TransactionType.HOLD);
    assertThat(response.quantity()).isEqualTo(2);
    assertThat(holding.getQuantity()).isEqualTo(2);
    assertThat(holding.getAveragePurchasePrice()).isEqualByComparingTo("100.00");
    assertThat(holding.isHidden()).isTrue();
    assertThat(holding.getReason()).isEqualTo("기존 근거");
    then(holdingRepository).should().findByPortfolioIdAndAssetId(null, 2L);
    then(holdingRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void decisionCannotAddAnAssetThatWasNotImported() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    assertThatThrownBy(
            () ->
                service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    then(holdingRepository).should().findByPortfolioIdAndAssetId(null, 2L);
    then(holdingRepository).shouldHaveNoMoreInteractions();
    then(transactionRepository).should().findByPortfolioIdAndRequestKey(null, "decision-1");
    then(transactionRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void duplicateDecisionReturnsExistingRecordWithoutApplyingPositionAgain() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    var existing = transaction(portfolio, TransactionType.HOLD);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(transactionRepository.findByPortfolioIdAndRequestKey(null, "decision-1"))
        .willReturn(Optional.of(existing));
    var response =
        service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision()));
    assertThat(response.reason()).isEqualTo("장기 성장");
    then(holdingRepository).shouldHaveNoInteractions();
    then(transactionRepository).should().findByPortfolioIdAndRequestKey(null, "decision-1");
    then(transactionRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void reviewAddsResultsWithoutRewritingTheInitialDecision() {
    var existing = transaction(SharedPortfolio.create(mock(Member.class)), TransactionType.HOLD);
    given(transactionRepository.findByIdAndPortfolioMemberId(7L, 1L))
        .willReturn(Optional.of(existing));
    given(reviewRepository.save(any(InvestmentReview.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    var response = service.addReview(1L, 7L, new ReviewRequest("실적 확인", "기대보다 느린 성장", "다음 실적 확인"));
    assertThat(response.actualResult()).isEqualTo("실적 확인");
    assertThat(existing.getDecisionReason()).isEqualTo("장기 성장");
    then(holdingRepository).shouldHaveNoInteractions();
  }

  @Test
  void holdingDecisionRetryRejectsChangedPayloadAndLegacyTradeKeys() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    var existingDecision = transaction(portfolio, TransactionType.HOLD);
    var existingTrade = transaction(portfolio, TransactionType.BUY);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(transactionRepository.findByPortfolioIdAndRequestKey(null, "decision-1"))
        .willReturn(Optional.of(existingDecision), Optional.of(existingTrade));
    var changed =
        new DecisionRequest(
            "다른 이유", HoldingPeriod.OVER_SIX_MONTHS, "매출 성장", "실적 역성장", new BigDecimal("10"));
    assertThatThrownBy(
            () -> service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, changed)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.DECISION_REQUEST_CONFLICT));
    assertThatThrownBy(
            () ->
                service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.DECISION_REQUEST_CONFLICT));
    then(holdingRepository).shouldHaveNoInteractions();
    then(transactionRepository).should(times(2)).findByPortfolioIdAndRequestKey(null, "decision-1");
  }

  @Test
  void anotherMembersPositionAndReviewCannotBeUsed() {
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mock(Member.class)));
    assertThatThrownBy(
            () ->
                service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    assertThatThrownBy(() -> service.addReview(1L, 7L, new ReviewRequest("결과", "차이", "다음 행동")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    then(holdingRepository).shouldHaveNoInteractions();
    then(reviewRepository).shouldHaveNoInteractions();
  }

  private DecisionRequest decision() {
    return new DecisionRequest(
        "장기 성장", HoldingPeriod.OVER_SIX_MONTHS, "매출 성장", "실적 역성장", new BigDecimal("10"));
  }

  private PortfolioTransaction transaction(SharedPortfolio portfolio, TransactionType type) {
    return new PortfolioTransaction(
        portfolio,
        asset(),
        "decision-1",
        type,
        2,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        LocalDate.now(),
        "장기 성장",
        HoldingPeriod.OVER_SIX_MONTHS,
        "매출 성장",
        "실적 역성장",
        new BigDecimal("10"),
        true);
  }

  private Asset asset() {
    Asset asset = mock(Asset.class);
    lenient().when(asset.getId()).thenReturn(2L);
    lenient().when(asset.getAssetCode()).thenReturn("005930");
    lenient().when(asset.getName()).thenReturn("삼성전자");
    lenient().when(asset.getCategory()).thenReturn(AssetCategory.STOCK);
    return asset;
  }
}
