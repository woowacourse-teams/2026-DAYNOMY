package org.grit.daynomy.league.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.OriginalPortfolioChanged;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueAutoSyncService {
  private final MemberRepository memberRepository;
  private final PortfolioRepository originalRepository;
  private final PortfolioHoldingRepository originalHoldings;
  private final SharedPortfolioRepository sharedRepository;
  private final SharedHoldingRepository sharedHoldings;
  private final SharedHoldingHistoryRepository histories;
  private final InvestmentDecisionRepository decisions;
  private final InvestorProfileRepository profiles;
  private final LeagueRankingCache rankingCache;
  private final PlatformTransactionManager transactionManager;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void afterOriginalChange(OriginalPortfolioChanged event) {
    try {
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      transaction.execute(
          status -> {
            reconcile(event.memberId());
            return null;
          });
    } catch (RuntimeException exception) {
      log.warn(
          "League auto sync will retry on the next scheduled run: memberId={}, errorType={}",
          event.memberId(),
          exception.getClass().getSimpleName());
    }
  }

  @Scheduled(cron = "${league.auto-sync.retry-cron:0 0 7,19 * * *}", zone = "Asia/Seoul")
  public void retrySharedPortfolios() {
    for (Long memberId : sharedRepository.findSharedMemberIds()) {
      afterOriginalChange(new OriginalPortfolioChanged(memberId));
    }
  }

  void reconcile(Long memberId) {
    var member = memberRepository.findByIdForUpdate(memberId).orElse(null);
    if (member == null) return;
    var originalPortfolio = originalRepository.findByMemberId(memberId).orElse(null);
    if (originalPortfolio == null) return;
    var shared =
        sharedRepository
            .findByMemberId(memberId)
            .orElseGet(() -> sharedRepository.save(SharedPortfolio.create(member)));
    List<PortfolioHolding> source =
        originalHoldings.findAllByPortfolioIdOrderById(originalPortfolio.getId());
    Map<Long, PortfolioHolding> sourceByAsset =
        source.stream()
            .filter(holding -> holding.getAsset().isListed())
            .filter(
                holding ->
                    holding.getAsset().getCategory() == AssetCategory.STOCK
                        || holding.getAsset().getCategory() == AssetCategory.ETF)
            .collect(Collectors.toMap(holding -> holding.getAsset().getId(), Function.identity()));
    Map<Long, String> reasons =
        InvestmentDecision.latestReasons(
            decisions.findAllByPortfolioMemberIdOrderByTradedOnDescIdDesc(memberId));
    List<SharedHolding> current = sharedHoldings.findAllByPortfolioIdOrderById(shared.getId());
    Map<Long, SharedHolding> currentByAsset =
        current.stream()
            .collect(Collectors.toMap(holding -> holding.getAsset().getId(), Function.identity()));
    boolean changed = false;
    for (PortfolioHolding original : sourceByAsset.values()) {
      Long assetId = original.getAsset().getId();
      SharedHolding holding = currentByAsset.get(assetId);
      if (holding == null) {
        holding =
            new SharedHolding(
                shared,
                original.getAsset(),
                original.getQuantity(),
                original.getAveragePurchasePrice());
        holding.changeVisibility(true);
        if (reasons.getOrDefault(assetId, "").isBlank()) holding.awaitReason();
        sharedHoldings.save(holding);
        histories.save(new SharedHoldingHistory(holding, ChangeType.ADDED));
        changed = true;
      } else if (holding.change(original.getQuantity(), original.getAveragePurchasePrice())) {
        sharedHoldings.save(holding);
        histories.save(new SharedHoldingHistory(holding, ChangeType.UPDATED));
        changed = true;
      }
    }
    for (SharedHolding holding : current) {
      if (sourceByAsset.containsKey(holding.getAsset().getId())) continue;
      histories.save(new SharedHoldingHistory(holding, ChangeType.REMOVED));
      sharedHoldings.delete(holding);
      changed = true;
    }
    if (changed && sourceByAsset.isEmpty()) {
      profiles
          .findByMemberId(memberId)
          .filter(profile -> profile.isLeagueEnabled())
          .ifPresent(
              profile -> profile.changePublication(false, false, false, false, Instant.now()));
    }
    if (changed) rankingCache.invalidateAfterCommit();
  }
}
