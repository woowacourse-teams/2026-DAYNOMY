package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.TransactionLog;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.DecisionListResponse;
import org.grit.daynomy.league.dto.LeagueDto.DecisionReasonRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionResponse;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewResponse;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InvestmentDecisionService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final InvestmentDecisionRepository decisionRepository;
  private final InvestmentReviewRepository reviewRepository;
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingRepository holdingRepository;
  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public DecisionListResponse getMine(Long memberId) {
    List<InvestmentDecision> transactions =
        decisionRepository.findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(memberId);
    List<Long> transactionIds = transactions.stream().map(InvestmentDecision::getId).toList();
    Map<Long, List<InvestmentReview>> reviews =
        transactionIds.isEmpty()
            ? Map.of()
            : reviewRepository.findAllByTransactionIdInOrderByCreatedAtAsc(transactionIds).stream()
                .collect(Collectors.groupingBy(review -> review.getTransaction().getId()));
    return new DecisionListResponse(
        transactions.stream()
            .map(
                transaction ->
                    DecisionResponse.from(
                        transaction, reviews.getOrDefault(transaction.getId(), List.of())))
            .toList());
  }

  /** 보유 당시의 판단만 기록한다. 원본·공유 자산의 수량과 매수가는 변경하지 않는다. */
  @Transactional
  public DecisionResponse recordDecision(Long memberId, DecisionRecordRequest request) {
    getMemberForUpdate(memberId);
    SharedPortfolio portfolio =
        portfolioRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    InvestmentDecision existing =
        decisionRepository
            .findByPortfolioIdAndRequestKey(portfolio.getId(), request.requestKey().trim())
            .orElse(null);
    if (existing != null) {
      if (existing.getTransactionType() != TransactionType.HOLD
          || !existing.getAsset().getId().equals(request.assetId())
          || !existing.matchesDecision(
              request.decision().reason(),
              request.decision().expectedHoldingPeriod(),
              request.decision().expectedChange(),
              request.decision().invalidationCondition(),
              request.decision().maximumAcceptableLossRate())) {
        throw new BusinessException(LeagueErrorCode.DECISION_REQUEST_CONFLICT);
      }
      log.atDebug()
          .addKeyValue("event", LogEvent.LEAGUE_DECISION_REPLAYED.code())
          .addKeyValue("memberId", memberId)
          .addKeyValue("portfolioId", portfolio.getId())
          .addKeyValue("decisionId", existing.getId())
          .log(LogEvent.LEAGUE_DECISION_REPLAYED.message());
      return DecisionResponse.from(
          existing, reviewRepository.findAllByTransactionIdOrderByCreatedAtAsc(existing.getId()));
    }
    SharedHolding holding =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), request.assetId())
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    var decision = request.decision();
    InvestmentDecision transaction =
        decisionRepository.save(
            new InvestmentDecision(
                portfolio,
                holding.getAsset(),
                request.requestKey().trim(),
                TransactionType.HOLD,
                holding.getQuantity(),
                holding.getAveragePurchasePrice(),
                BigDecimal.ZERO,
                LocalDate.now(SEOUL),
                decision.reason(),
                decision.expectedHoldingPeriod(),
                decision.expectedChange(),
                decision.invalidationCondition(),
                decision.maximumAcceptableLossRate(),
                true));
    if (holding.isPendingReason()) {
      holding.activate();
    }
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_DECISION_RECORDED,
        log.atInfo()
            .addKeyValue("memberId", memberId)
            .addKeyValue("portfolioId", portfolio.getId())
            .addKeyValue("decisionId", transaction.getId())
            .addKeyValue("transactionType", transaction.getTransactionType()));
    return DecisionResponse.from(transaction, List.of());
  }

  @Transactional
  public DecisionResponse reviseReason(
      Long memberId, Long transactionId, DecisionReasonRequest request) {
    getMemberForUpdate(memberId);
    InvestmentDecision transaction =
        decisionRepository
            .findByIdAndPortfolioMemberId(transactionId, memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    transaction.reviseReason(request.reason());
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_DECISION_REASON_REVISED,
        log.atInfo().addKeyValue("memberId", memberId).addKeyValue("decisionId", transactionId));
    return DecisionResponse.from(
        transaction, reviewRepository.findAllByTransactionIdOrderByCreatedAtAsc(transactionId));
  }

  @Transactional
  public ReviewResponse addReview(Long memberId, Long transactionId, ReviewRequest request) {
    InvestmentDecision transaction =
        decisionRepository
            .findByIdAndPortfolioMemberId(transactionId, memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    InvestmentReview saved =
        reviewRepository.save(
            new InvestmentReview(
                transaction,
                request.actualResult(),
                request.differenceFromExpectation(),
                request.nextAction()));
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_REVIEW_CREATED,
        log.atInfo()
            .addKeyValue("memberId", memberId)
            .addKeyValue("decisionId", transactionId)
            .addKeyValue("reviewId", saved.getId()));
    return ReviewResponse.from(saved);
  }

  @Transactional
  public ReviewResponse reviseReview(
      Long memberId, Long transactionId, Long reviewId, ReviewRequest request) {
    InvestmentReview review =
        reviewRepository
            .findByIdAndTransactionIdAndTransactionPortfolioMemberId(
                reviewId, transactionId, memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.REVIEW_NOT_FOUND));
    review.revise(
        request.actualResult(), request.differenceFromExpectation(), request.nextAction());
    reviewRepository.flush();
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_REVIEW_REVISED,
        log.atInfo()
            .addKeyValue("memberId", memberId)
            .addKeyValue("decisionId", transactionId)
            .addKeyValue("reviewId", reviewId));
    return ReviewResponse.from(review);
  }

  private Member getMemberForUpdate(Long memberId) {
    return memberRepository
        .findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
  }
}
