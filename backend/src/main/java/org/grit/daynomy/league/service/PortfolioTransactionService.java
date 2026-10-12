package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.PortfolioTransaction;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewResponse;
import org.grit.daynomy.league.dto.LeagueDto.TransactionListResponse;
import org.grit.daynomy.league.dto.LeagueDto.TransactionResponse;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.PortfolioTransactionRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioTransactionService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final PortfolioTransactionRepository transactionRepository;
  private final InvestmentReviewRepository reviewRepository;
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingRepository holdingRepository;
  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public TransactionListResponse getMine(Long memberId) {
    List<PortfolioTransaction> transactions =
        transactionRepository.findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(memberId);
    List<Long> transactionIds = transactions.stream().map(PortfolioTransaction::getId).toList();
    Map<Long, List<InvestmentReview>> reviews =
        transactionIds.isEmpty()
            ? Map.of()
            : reviewRepository.findAllByTransactionIdInOrderByCreatedAtAsc(transactionIds).stream()
                .collect(Collectors.groupingBy(review -> review.getTransaction().getId()));
    return new TransactionListResponse(
        transactions.stream()
            .map(
                transaction ->
                    TransactionResponse.from(
                        transaction, reviews.getOrDefault(transaction.getId(), List.of())))
            .toList());
  }

  /** 보유 당시의 판단만 기록한다. 원본·공유 자산의 수량과 매수가는 변경하지 않는다. */
  @Transactional
  public TransactionResponse recordDecision(Long memberId, DecisionRecordRequest request) {
    getMemberForUpdate(memberId);
    SharedPortfolio portfolio =
        portfolioRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    PortfolioTransaction existing =
        transactionRepository
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
      return TransactionResponse.from(
          existing, reviewRepository.findAllByTransactionIdOrderByCreatedAtAsc(existing.getId()));
    }
    SharedHolding holding =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), request.assetId())
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    var decision = request.decision();
    PortfolioTransaction transaction =
        transactionRepository.save(
            new PortfolioTransaction(
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
    return TransactionResponse.from(transaction, List.of());
  }

  @Transactional
  public ReviewResponse addReview(Long memberId, Long transactionId, ReviewRequest request) {
    PortfolioTransaction transaction =
        transactionRepository
            .findByIdAndPortfolioMemberId(transactionId, memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.TRANSACTION_NOT_FOUND));
    return ReviewResponse.from(
        reviewRepository.save(
            new InvestmentReview(
                transaction,
                request.actualResult(),
                request.differenceFromExpectation(),
                request.nextAction())));
  }

  private Member getMemberForUpdate(Long memberId) {
    return memberRepository
        .findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
  }
}
