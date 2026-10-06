package org.grit.daynomy.finance.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.finance.domain.LearningProgress;
import org.grit.daynomy.finance.domain.SimulatedTrade;
import org.grit.daynomy.finance.domain.SimulatedTradeType;
import org.grit.daynomy.finance.domain.WeeklyCheckIn;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInListResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInRequest;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeCreateRequest;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeListResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressListResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressResponse;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressUpdateRequest;
import org.grit.daynomy.finance.exception.FinancialLearningErrorCode;
import org.grit.daynomy.finance.repository.LearningProgressRepository;
import org.grit.daynomy.finance.repository.SimulatedTradeRepository;
import org.grit.daynomy.finance.repository.WeeklyCheckInRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class FinancialLearningService {

  private final LearningProgressRepository progressRepository;
  private final WeeklyCheckInRepository checkInRepository;
  private final SimulatedTradeRepository mockTradeRepository;
  private final MemberRepository memberRepository;
  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public ProgressListResponse progress(Long memberId) {
    return new ProgressListResponse(
        progressRepository.findAllByMemberIdOrderByUpdatedAtDesc(memberId).stream()
            .map(ProgressResponse::from)
            .toList());
  }

  @Transactional
  public ProgressResponse updateProgress(
      Long memberId, String itemKey, ProgressUpdateRequest request) {
    String normalizedItemKey = itemKey.trim();
    LearningProgress progress =
        progressRepository
            .findByMemberIdAndItemKey(memberId, normalizedItemKey)
            .orElseGet(
                () ->
                    LearningProgress.create(
                        getMember(memberId), normalizedItemKey, request.itemType()));
    progress.change(request.itemType(), request.completed(), request.bookmarked());
    return ProgressResponse.from(progressRepository.save(progress));
  }

  @Transactional(readOnly = true)
  public CheckInListResponse checkIns(Long memberId, int limit) {
    return new CheckInListResponse(
        checkInRepository.findAllByMemberIdOrderByWeekStartDesc(memberId).stream()
            .limit(limit)
            .map(CheckInResponse::from)
            .toList());
  }

  @Transactional
  public CheckInResponse saveCheckIn(Long memberId, LocalDate weekStart, CheckInRequest request) {
    if (weekStart.getDayOfWeek().getValue() != 1) {
      throw new BusinessException(FinancialLearningErrorCode.INVALID_CHECK_IN_WEEK_START);
    }
    WeeklyCheckIn checkIn =
        checkInRepository
            .findByMemberIdAndWeekStart(memberId, weekStart)
            .orElseGet(() -> WeeklyCheckIn.create(getMember(memberId), weekStart));
    checkIn.change(
        request.targetSavings(),
        request.targetInvestment(),
        request.targetDebtPayment(),
        request.actualSavings(),
        request.actualInvestment(),
        request.actualDebtPayment(),
        request.note());
    return CheckInResponse.from(checkInRepository.save(checkIn));
  }

  @Transactional(readOnly = true)
  public MockTradeListResponse mockTrades(Long memberId) {
    return new MockTradeListResponse(
        mockTradeRepository.findAllByMemberIdOrderByTradedOnDescIdDesc(memberId).stream()
            .map(MockTradeResponse::from)
            .toList());
  }

  @Transactional
  public MockTradeResponse addMockTrade(Long memberId, MockTradeCreateRequest request) {
    Member member = getMemberForUpdate(memberId);
    Asset asset =
        assetRepository
            .findById(request.assetId())
            .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
    if (!asset.isListed()
        || (asset.getCategory() != AssetCategory.STOCK
            && asset.getCategory() != AssetCategory.ETF)) {
      throw new BusinessException(FinancialLearningErrorCode.INVALID_MOCK_TRADE_ASSET);
    }
    if (request.tradeType() == SimulatedTradeType.SELL
        && request.quantity() > mockQuantity(memberId, request.assetId())) {
      throw new BusinessException(FinancialLearningErrorCode.MOCK_SELL_QUANTITY_EXCEEDED);
    }
    SimulatedTrade trade =
        new SimulatedTrade(
            member,
            asset,
            request.tradeType(),
            request.quantity(),
            request.price(),
            request.reason(),
            request.tradedOn());
    return MockTradeResponse.from(mockTradeRepository.save(trade));
  }

  @Transactional
  public void removeMockTrade(Long memberId, Long tradeId) {
    getMemberForUpdate(memberId);
    SimulatedTrade trade =
        mockTradeRepository
            .findByIdAndMemberId(tradeId, memberId)
            .orElseThrow(
                () -> new BusinessException(FinancialLearningErrorCode.MOCK_TRADE_NOT_FOUND));
    if (trade.getTradeType() == SimulatedTradeType.BUY
        && mockQuantity(memberId, trade.getAsset().getId()) < trade.getQuantity()) {
      throw new BusinessException(FinancialLearningErrorCode.MOCK_TRADE_DELETE_INVALID);
    }
    mockTradeRepository.delete(trade);
  }

  private long mockQuantity(Long memberId, Long assetId) {
    return mockTradeRepository.findAllByMemberIdOrderByTradedOnDescIdDesc(memberId).stream()
        .filter(trade -> trade.getAsset().getId().equals(assetId))
        .mapToLong(
            trade ->
                trade.getTradeType() == SimulatedTradeType.BUY
                    ? trade.getQuantity()
                    : -trade.getQuantity())
        .sum();
  }

  private Member getMember(Long memberId) {
    return memberRepository
        .findById(memberId)
        .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
  }

  private Member getMemberForUpdate(Long memberId) {
    return memberRepository
        .findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
  }
}
