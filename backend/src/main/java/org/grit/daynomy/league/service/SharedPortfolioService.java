package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.SharedPortfolioDto.*;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 읽기도 쓰기도 공유용 테이블만 사용한다. 원본은 명시적 입력 사본으로만 가져온다. */
@RequiredArgsConstructor
@Service
public class SharedPortfolioService {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingRepository holdingRepository;
  private final SharedHoldingHistoryRepository historyRepository;
  private final MemberRepository memberRepository;
  private final AssetRepository assetRepository;
  private final StockDailyPriceRepository priceRepository;

  @Transactional(readOnly = true)
  public PortfolioResponse getMine(Long memberId) {
    return response(portfolioRepository.findByMemberId(memberId).orElse(null));
  }

  @Transactional
  public PortfolioResponse importHoldings(Long memberId, ImportRequest request) {
    SharedPortfolio portfolio = getForUpdate(memberId);
    HashSet<Long> seen = new HashSet<>();
    // 중간에 검증이 실패해도 트랜잭션이 앞서 저장한 자산과 이력까지 롤백한다.
    for (HoldingInput input : request.holdings()) {
      if (!seen.add(input.assetId()))
        throw new BusinessException(LeagueErrorCode.DUPLICATE_SHARED_ASSET);
      SharedHolding holding =
          holdingRepository
              .findByPortfolioIdAndAssetId(portfolio.getId(), input.assetId())
              .orElse(null);
      if (holding != null && !request.overwriteExisting()) {
        throw new BusinessException(LeagueErrorCode.SHARED_IMPORT_CONFLICT);
      }
      ChangeType changeType = holding == null ? ChangeType.ADDED : ChangeType.UPDATED;
      if (holding == null) {
        holding =
            new SharedHolding(
                portfolio, asset(input.assetId()), input.quantity(), input.averagePurchasePrice());
      } else {
        // 재가져오기로 숨김과 판단 근거를 덮어쓰지 않는다.
        if (!holding.change(input.quantity(), input.averagePurchasePrice())) continue;
      }
      holdingRepository.save(holding);
      historyRepository.save(new SharedHoldingHistory(holding, changeType));
    }
    return response(portfolio);
  }

  @Transactional
  public PortfolioResponse changeVisibility(
      Long memberId, Long assetId, VisibilityRequest request) {
    SharedPortfolio portfolio = getForUpdate(memberId);
    SharedHolding holding =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), assetId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    holding.changeVisibility(request.hidden());
    return response(portfolio);
  }

  private SharedPortfolio getForUpdate(Long memberId) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    return portfolioRepository
        .findByMemberId(memberId)
        .orElseGet(() -> portfolioRepository.save(SharedPortfolio.create(member)));
  }

  private Asset asset(Long assetId) {
    Asset asset = assetRepository.findById(assetId).orElse(null);
    if (asset == null
        || !asset.isListed()
        || (asset.getCategory() != AssetCategory.STOCK
            && asset.getCategory() != AssetCategory.ETF)) {
      throw new BusinessException(LeagueErrorCode.INVALID_LEAGUE_ASSET);
    }
    return asset;
  }

  private PortfolioResponse response(SharedPortfolio portfolio) {
    List<SharedHolding> holdings =
        portfolio == null
            ? List.of()
            : holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId());
    // 동일한 기준일의 가격이 없는 종목은 추정가로 대체하지 않는다.
    java.time.LocalDate baseDate =
        priceRepository.findDistinctBaseDatesOrderByBaseDate().stream()
            .max(java.time.LocalDate::compareTo)
            .orElse(null);
    List<HoldingResponse> values =
        holdings.stream()
            .map(
                h -> {
                  StockDailyPrice price =
                      baseDate == null
                          ? null
                          : priceRepository
                              .findByAssetIdAndBaseDate(h.getAsset().getId(), baseDate)
                              .orElse(null);
                  BigDecimal evaluation =
                      price == null
                          ? null
                          : price.getClosePrice().multiply(BigDecimal.valueOf(h.getQuantity()));
                  return new HoldingResponse(
                      h.getAsset().getId(),
                      h.getAsset().getAssetCode(),
                      h.getAsset().getName(),
                      h.getAsset().getCategory().name(),
                      h.getAsset().getMarket().name(),
                      h.getQuantity(),
                      h.getAveragePurchasePrice(),
                      h.isHidden(),
                      h.getReason(),
                      price == null ? null : price.getBaseDate(),
                      price == null ? null : price.getClosePrice(),
                      evaluation,
                      price == null
                          ? null
                          : rate(price.getClosePrice(), h.getAveragePurchasePrice()));
                })
            .toList();
    BigDecimal cost =
        holdings.stream()
            .map(h -> h.getAveragePurchasePrice().multiply(BigDecimal.valueOf(h.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean complete = values.stream().allMatch(h -> h.evaluationAmount() != null);
    BigDecimal evaluation =
        complete
            ? values.stream()
                .map(HoldingResponse::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : null;
    return new PortfolioResponse(
        values,
        cost,
        evaluation,
        complete && cost.signum() > 0 ? rate(evaluation, cost) : null,
        complete);
  }

  private BigDecimal rate(BigDecimal value, BigDecimal cost) {
    return value.subtract(cost).multiply(HUNDRED).divide(cost, 2, RoundingMode.HALF_UP);
  }
}
