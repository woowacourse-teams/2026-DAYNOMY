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
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.LeagueTypes.HoldingPeriod;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.DecisionReasonRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionResponse;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentDecisionServiceTest {
  @Mock private InvestmentDecisionRepository decisionRepository;
  @Mock private InvestmentReviewRepository reviewRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private MemberRepository memberRepository;
  @InjectMocks private InvestmentDecisionService service;

  @Test
  void holdingDecisionSnapshotsPositionWithoutChangingQuantityPriceOrVisibility() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    SharedHolding holding = new SharedHolding(portfolio, asset(), 2, new BigDecimal("100.00"));
    holding.describe(true, "기존 근거");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(null, 2L)).willReturn(Optional.of(holding));
    given(decisionRepository.save(any(InvestmentDecision.class)))
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
    then(decisionRepository).should().findByPortfolioIdAndRequestKey(null, "decision-1");
    then(decisionRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void duplicateDecisionReturnsExistingRecordWithoutApplyingPositionAgain() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    var existing = transaction(portfolio, TransactionType.HOLD);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(decisionRepository.findByPortfolioIdAndRequestKey(null, "decision-1"))
        .willReturn(Optional.of(existing));
    var response =
        service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision()));
    assertThat(response.reason()).isEqualTo("장기 성장");
    then(holdingRepository).shouldHaveNoInteractions();
    then(decisionRepository).should().findByPortfolioIdAndRequestKey(null, "decision-1");
    then(decisionRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void originalRequestCanBeRetriedAfterMultipleReasonEdits() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    var existing = transaction(portfolio, TransactionType.HOLD);
    existing.reviseReason("수정한 이유");
    existing.reviseReason("최종 이유");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(decisionRepository.findByPortfolioIdAndRequestKey(null, "decision-1"))
        .willReturn(Optional.of(existing));

    var response =
        service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, decision()));

    assertThat(response.reason()).isEqualTo("최종 이유");
    then(holdingRepository).shouldHaveNoInteractions();
    then(decisionRepository).should().findByPortfolioIdAndRequestKey(null, "decision-1");
    then(decisionRepository).shouldHaveNoMoreInteractions();
  }

  @ParameterizedTest
  @ValueSource(strings = {"수정한 이유", "최종 이유"})
  void editedReasonCannotReuseOriginalRequestKey(String reason) {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    var existing = transaction(portfolio, TransactionType.HOLD);
    existing.reviseReason("수정한 이유");
    existing.reviseReason("최종 이유");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(decisionRepository.findByPortfolioIdAndRequestKey(null, "decision-1"))
        .willReturn(Optional.of(existing));
    var changed =
        new DecisionRequest(
            reason, HoldingPeriod.OVER_SIX_MONTHS, "매출 성장", "실적 역성장", new BigDecimal("10"));

    assertThatThrownBy(
            () -> service.recordDecision(1L, new DecisionRecordRequest("decision-1", 2L, changed)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.DECISION_REQUEST_CONFLICT));
    assertThat(existing.getDecisionReason()).isEqualTo("최종 이유");
    then(holdingRepository).shouldHaveNoInteractions();
  }

  @Test
  void reviewAddsResultsWithoutRewritingTheInitialDecision() {
    var existing = transaction(SharedPortfolio.create(mock(Member.class)), TransactionType.HOLD);
    given(decisionRepository.findByIdAndPortfolioMemberId(7L, 1L))
        .willReturn(Optional.of(existing));
    given(reviewRepository.save(any(InvestmentReview.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    var response = service.addReview(1L, 7L, new ReviewRequest("실적 확인", "기대보다 느린 성장", "다음 실적 확인"));
    assertThat(response.actualResult()).isEqualTo("실적 확인");
    assertThat(existing.getDecisionReason()).isEqualTo("장기 성장");
    then(holdingRepository).shouldHaveNoInteractions();
  }

  @Test
  void revisesOwnedReviewWithoutChangingTheInitialDecision() {
    var transaction = transaction(SharedPortfolio.create(mock(Member.class)), TransactionType.HOLD);
    var review = new InvestmentReview(transaction, "기존 결과", "기존 차이", "기존 행동");
    given(reviewRepository.findByIdAndTransactionIdAndTransactionPortfolioMemberId(8L, 7L, 1L))
        .willReturn(Optional.of(review));

    var response = service.reviseReview(1L, 7L, 8L, new ReviewRequest(" 수정 결과 ", "수정 차이", "수정 행동"));

    assertThat(response.actualResult()).isEqualTo("수정 결과");
    assertThat(review.getNextAction()).isEqualTo("수정 행동");
    assertThat(transaction.getDecisionReason()).isEqualTo("장기 성장");
    then(reviewRepository).should().flush();
  }

  @Test
  void cannotReviseAnotherMembersReview() {
    assertThatThrownBy(
            () -> service.reviseReview(1L, 7L, 8L, new ReviewRequest("결과", "차이", "다음 행동")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.REVIEW_NOT_FOUND));
    then(reviewRepository)
        .should()
        .findByIdAndTransactionIdAndTransactionPortfolioMemberId(8L, 7L, 1L);
    then(reviewRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void holdingDecisionRetryRejectsChangedPayloadAndLegacyTradeKeys() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    var existingDecision = transaction(portfolio, TransactionType.HOLD);
    var existingTrade = transaction(portfolio, TransactionType.BUY);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(decisionRepository.findByPortfolioIdAndRequestKey(null, "decision-1"))
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
    then(decisionRepository).should(times(2)).findByPortfolioIdAndRequestKey(null, "decision-1");
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

  @ParameterizedTest
  @EnumSource(TransactionType.class)
  void revisesOnlyReasonAndPreservesPositionAndReviews(TransactionType type) {
    var existing = transaction(SharedPortfolio.create(mock(Member.class)), type);
    var review = new InvestmentReview(existing, "결과", "차이", "다음 행동");
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mock(Member.class)));
    given(decisionRepository.findByIdAndPortfolioMemberId(7L, 1L))
        .willReturn(Optional.of(existing));
    given(reviewRepository.findAllByTransactionIdOrderByCreatedAtAsc(7L))
        .willReturn(List.of(review));
    var original = DecisionResponse.from(existing, List.of(review));

    var response = service.reviseReason(1L, 7L, new DecisionReasonRequest(" 수정한 이유 "));

    assertThat(response.reason()).isEqualTo("수정한 이유");
    assertThat(response).usingRecursiveComparison().ignoringFields("reason").isEqualTo(original);
    then(holdingRepository).shouldHaveNoInteractions();
    then(portfolioRepository).shouldHaveNoInteractions();
    then(decisionRepository).should().findByIdAndPortfolioMemberId(7L, 1L);
    then(decisionRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void cannotReviseMissingOrAnotherMembersDecision() {
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mock(Member.class)));
    assertThatThrownBy(() -> service.reviseReason(1L, 7L, new DecisionReasonRequest("수정한 이유")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    then(decisionRepository).should().findByIdAndPortfolioMemberId(7L, 1L);
    then(reviewRepository).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void blankReasonDoesNotOverwriteOriginal(String reason) {
    var existing = transaction(SharedPortfolio.create(mock(Member.class)), TransactionType.HOLD);
    assertThatThrownBy(() -> existing.reviseReason(reason))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(existing.getDecisionReason()).isEqualTo("장기 성장");
    assertThat(existing.getOriginalDecisionReason()).isNull();
  }

  @Test
  void reasonOnlyDecisionStoresNoInventedOptionalValuesAndCanBeRetried() {
    Member member = mock(Member.class);
    SharedPortfolio portfolio = SharedPortfolio.create(member);
    SharedHolding holding = new SharedHolding(portfolio, asset(), 2, new BigDecimal("100.00"));
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(null, 2L)).willReturn(Optional.of(holding));
    given(decisionRepository.save(any(InvestmentDecision.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    var input = new DecisionRequest(" 보유 이유 ", null, null, null, null);
    var response = service.recordDecision(1L, new DecisionRecordRequest("reason-only", 2L, input));
    assertThat(response.reason()).isEqualTo("보유 이유");
    assertThat(response.expectedHoldingPeriod()).isNull();
    assertThat(response.maximumAcceptableLossRate()).isNull();
    assertThat(response.expectedChange()).isEmpty();
    var saved =
        new InvestmentDecision(
            portfolio,
            holding.getAsset(),
            "reason-only",
            TransactionType.HOLD,
            2,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            LocalDate.now(),
            "보유 이유",
            null,
            "",
            "",
            null,
            true);
    assertThat(saved.matchesDecision(" 보유 이유 ", null, null, "", null)).isTrue();
    assertThat(saved.matchesDecision("보유 이유", null, "", "", BigDecimal.ZERO)).isFalse();
  }

  private InvestmentDecision transaction(SharedPortfolio portfolio, TransactionType type) {
    return new InvestmentDecision(
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
