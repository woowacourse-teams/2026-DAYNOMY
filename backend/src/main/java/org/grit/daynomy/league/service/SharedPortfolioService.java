package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.TransactionLog;
import org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.SharedPortfolioDto.*;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 읽기도 쓰기도 공유용 테이블만 사용한다. 원본은 명시적 입력 사본으로만 가져온다. */
@Slf4j
@RequiredArgsConstructor
@Service
public class SharedPortfolioService {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingRepository holdingRepository;
  private final SharedHoldingHistoryRepository historyRepository;
  private final MemberRepository memberRepository;
  private final AssetRepository assetRepository;
  private final StockPriceService stockPriceService;
  private final StockDailyPriceRepository priceRepository;
  private final UsdKrwRateRepository exchangeRates;
  private final MarketTradingDayRepository tradingDayRepository;
  private final InvestorProfileRepository profileRepository;
  private final InvestmentDecisionRepository decisionRepository;
  private final InvestmentDecisionService decisionService;
  private final LeagueRankingCache rankingCache;
  private final LeagueAutoSyncService autoSyncService;
  private final PortfolioHoldingRepository originalHoldings;

  @Transactional
  public PortfolioResponse getMine(Long memberId) {
    autoSyncService.reconcile(memberId);
    return response(portfolioRepository.findByMemberId(memberId).orElse(null));
  }

  @Transactional
  public PortfolioResponse importHoldings(Long memberId, ImportRequest request) {
    SharedPortfolio portfolio = getForUpdate(memberId);
    boolean participating =
        profileRepository
            .findByMemberId(memberId)
            .map(profile -> profile.isLeagueEnabled())
            .orElse(false);
    Map<Long, String> reasons =
        InvestmentDecision.latestReasons(
            decisionRepository.findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(memberId));
    HashSet<Long> seen = new HashSet<>();
    int added = 0;
    int updated = 0;
    int unchanged = 0;
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
      boolean needsReason =
          holding == null
              ? !reasons.containsKey(input.assetId())
              : holding.reasonFrom(reasons).isBlank();
      if (participating
          && (holding == null || !holding.isHidden())
          && needsReason
          && input.decision() == null) {
        throw new BusinessException(LeagueErrorCode.HOLDING_REASON_REQUIRED);
      }
      ChangeType changeType = holding == null ? ChangeType.ADDED : ChangeType.UPDATED;
      boolean changed = true;
      if (holding == null) {
        holding =
            new SharedHolding(
                portfolio, asset(input.assetId()), input.quantity(), input.averagePurchasePrice());
      } else {
        // 재가져오기로 숨김과 판단 근거를 덮어쓰지 않는다.
        changed = holding.change(input.quantity(), input.averagePurchasePrice());
      }
      if (changed) holdingRepository.save(holding);
      if (needsReason && input.decision() != null) {
        decisionService.recordDecision(
            memberId,
            new DecisionRecordRequest(
                UUID.randomUUID().toString(), input.assetId(), input.decision()));
      }
      if (changed) {
        historyRepository.save(new SharedHoldingHistory(holding, changeType));
      }
      if (changed) {
        if (changeType == ChangeType.ADDED) added++;
        else updated++;
      } else unchanged++;
    }
    PortfolioResponse result = response(portfolio);
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_HOLDINGS_IMPORTED,
        log.atInfo()
            .addKeyValue("memberId", memberId)
            .addKeyValue("portfolioId", portfolio.getId())
            .addKeyValue("requestedCount", request.holdings().size())
            .addKeyValue("addedCount", added)
            .addKeyValue("updatedCount", updated)
            .addKeyValue("unchangedCount", unchanged)
            .addKeyValue("holdingCount", result.holdings().size())
            .addKeyValue("overwriteExisting", request.overwriteExisting()));
    return result;
  }

  @Transactional
  public PortfolioResponse changeVisibility(
      Long memberId, Long assetId, VisibilityRequest request) {
    SharedPortfolio portfolio = getForUpdate(memberId);
    SharedHolding holding =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), assetId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    if (!request.hidden()) {
      Map<Long, String> reasons =
          InvestmentDecision.latestReasons(
              decisionRepository.findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(memberId));
      if (holding.reasonFrom(reasons).isBlank())
        throw new BusinessException(LeagueErrorCode.HOLDING_REASON_REQUIRED);
    }
    boolean previousHidden = holding.isHidden();
    holding.changeVisibility(request.hidden());
    PortfolioResponse result = response(portfolio);
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_HOLDING_VISIBILITY_CHANGED,
        (previousHidden != holding.isHidden() ? log.atInfo() : log.atDebug())
            .addKeyValue("memberId", memberId)
            .addKeyValue("portfolioId", portfolio.getId())
            .addKeyValue("holdingId", holding.getId())
            .addKeyValue("previousHidden", previousHidden)
            .addKeyValue("hidden", holding.isHidden()));
    return result;
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

  @Transactional
  public PortfolioResponse removeHolding(Long memberId, Long assetId) {
    SharedPortfolio portfolio = getForUpdate(memberId);
    if (originalHoldings.existsByPortfolioMemberIdAndAssetId(memberId, assetId))
      throw new BusinessException(LeagueErrorCode.SHARED_HOLDING_SOURCE_MANAGED);
    SharedHolding holding =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), assetId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.SHARED_HOLDING_NOT_FOUND));
    historyRepository.save(new SharedHoldingHistory(holding, ChangeType.REMOVED));
    holdingRepository.delete(holding);
    PortfolioResponse remaining = response(portfolio);
    if (remaining.holdings().isEmpty()) {
      rankingCache.invalidateAfterCommit();
      profileRepository
          .findByMemberId(memberId)
          .ifPresent(
              profile -> {
                boolean wasParticipating = profile.isLeagueEnabled();
                profile.changePublication(false, false, false, false, Instant.now());
                if (wasParticipating) {
                  TransactionLog.afterCommit(
                      LogEvent.LEAGUE_PARTICIPATION_ENDED,
                      log.atInfo()
                          .addKeyValue("memberId", memberId)
                          .addKeyValue("profileId", profile.getId())
                          .addKeyValue("portfolioId", portfolio.getId())
                          .addKeyValue("previousLeagueEnabled", true)
                          .addKeyValue("leagueEnabled", false)
                          .addKeyValue("reason", "LAST_HOLDING_REMOVED"));
                }
              });
    }
    TransactionLog.afterCommit(
        LogEvent.LEAGUE_HOLDING_REMOVED,
        log.atInfo()
            .addKeyValue("memberId", memberId)
            .addKeyValue("portfolioId", portfolio.getId())
            .addKeyValue("holdingId", holding.getId())
            .addKeyValue("remainingHoldingCount", remaining.holdings().size()));
    return remaining;
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
    LocalDate baseDate =
        tradingDayRepository
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                LocalDate.now(ZoneId.of("Asia/Seoul")))
            .map(MarketTradingDay::getBaseDate)
            .orElse(null);
    Map<Long, StockDailyPrice> prices =
        stockPriceService.pricesAt(
            holdings.stream()
                .filter(h -> !"USD".equals(h.getAsset().getCurrency()))
                .map(h -> h.getAsset().getId())
                .toList(),
            baseDate);
    Map<Long, String> reasons =
        portfolio == null
            ? Map.of()
            : InvestmentDecision.latestReasons(
                decisionRepository.findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(
                    portfolio.getMember().getId()));
    Set<Long> sourceAssetIds =
        portfolio == null
            ? Set.of()
            : originalHoldings.findAllByPortfolioMemberId(portfolio.getMember().getId()).stream()
                .map(holding -> holding.getAsset().getId())
                .collect(Collectors.toSet());
    List<HoldingResponse> values =
        holdings.stream()
            .map(
                h -> {
                  StockDailyPrice price =
                      "USD".equals(h.getAsset().getCurrency())
                          ? priceRepository
                              .findFirstByAssetIdAndBaseDateLessThanEqualOrderByBaseDateDesc(
                                  h.getAsset().getId(), LocalDate.now(ZoneId.of("Asia/Seoul")))
                              .orElse(null)
                          : prices.get(h.getAsset().getId());
                  BigDecimal priceKrw =
                      price == null
                          ? null
                          : "USD".equals(h.getAsset().getCurrency())
                              ? exchangeRates
                                  .findById(price.getBaseDate())
                                  .map(rate -> price.getClosePrice().multiply(rate.getRate()))
                                  .orElse(null)
                              : price.getClosePrice();
                  BigDecimal evaluation =
                      priceKrw == null
                          ? null
                          : priceKrw.multiply(BigDecimal.valueOf(h.getQuantity()));
                  return new HoldingResponse(
                      h.getAsset().getId(),
                      h.getAsset().getAssetCode(),
                      h.getAsset().getName(),
                      h.getAsset().getCategory().name(),
                      h.getAsset().getMarket().name(),
                      h.getAsset().getCurrency(),
                      h.getQuantity(),
                      h.getAveragePurchasePrice(),
                      h.isHidden(),
                      h.isPendingReason(),
                      sourceAssetIds.contains(h.getAsset().getId()),
                      h.reasonFrom(reasons),
                      price == null ? null : price.getBaseDate(),
                      price == null ? null : price.getClosePrice(),
                      evaluation,
                      priceKrw == null ? null : rate(priceKrw, h.getAveragePurchasePrice()));
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
