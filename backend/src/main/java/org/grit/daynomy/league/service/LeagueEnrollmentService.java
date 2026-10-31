package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator.Position;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeagueEnrollmentService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final PortfolioRepository originalRepository;
  private final PortfolioHoldingHistoryRepository originalHistoryRepository;
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingRepository holdingRepository;
  private final SharedHoldingHistoryRepository historyRepository;
  private final MarketTradingDayRepository tradingDayRepository;
  private final PortfolioLedgerCalculator ledgerCalculator;
  private final PortfolioDailySnapshotRepository snapshotRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void includeCurrentWeek(InvestorProfile profile) {
    // 재참여로 이미 집계한 성과나 미참여 구간을 덮어쓰지 않는다.
    if (profile.getParticipations().size() != 1) return;
    var period = profile.getParticipations().getFirst();
    if (period.getPerformanceStartedAt() != null) return;
    var original = originalRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    var shared = portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    if (original == null || shared == null) return;
    var holdings = holdingRepository.findAllByPortfolioIdOrderById(shared.getId());
    Set<Long> assetIds =
        holdings.stream().map(h -> h.getAsset().getId()).collect(Collectors.toSet());
    if (assetIds.isEmpty()) return;
    LocalDate joined = period.getStartedAt().atZone(SEOUL).toLocalDate();
    LocalDate week = joined.with(java.time.DayOfWeek.MONDAY);
    var snapshots =
        snapshotRepository
            .findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(original.getId(), week, joined)
            .stream()
            .map(PortfolioDailySnapshot::getBaseDate)
            .collect(Collectors.toSet());
    if (snapshots.isEmpty()) return;
    var histories =
        originalHistoryRepository.findOrderedWithAssetsByPortfolioId(original.getId()).stream()
            .filter(
                h ->
                    h.getAsset().isListed()
                        && (h.getAsset().getCategory() == AssetCategory.STOCK
                            || h.getAsset().getCategory() == AssetCategory.ETF))
            .filter(
                h -> h.getCreatedAt() == null || !h.getCreatedAt().isAfter(period.getStartedAt()))
            .toList();
    if (!histories.stream()
        .map(h -> h.getAsset().getId())
        .collect(Collectors.toSet())
        .containsAll(assetIds)) return;
    Map<Long, Asset> assets = new LinkedHashMap<>();
    histories.forEach(
        history -> assets.putIfAbsent(history.getAsset().getId(), history.getAsset()));
    var days = tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, joined);
    List<SharedHoldingHistory> copies = new ArrayList<>();
    Instant from = null;
    for (var day : days) {
      var closedAssets =
          assets.values().stream()
              .filter(
                  asset ->
                      marketOpen(asset, day)
                          && marketClose(asset, day).isBefore(period.getStartedAt()))
              .toList();
      if (closedAssets.isEmpty()) continue;
      var source =
          histories.stream().filter(h -> !h.getEffectiveDate().isAfter(day.getBaseDate())).toList();
      var positions = ledgerCalculator.replay(source).positions();
      if (from == null) {
        if (!snapshots.contains(day.getBaseDate()) || positions.isEmpty()) continue;
        from =
            closedAssets.stream()
                .map(asset -> marketClose(asset, day))
                .max(Comparator.naturalOrder())
                .orElseThrow();
        var opening =
            source.stream().filter(h -> h.getEffectiveDate().isBefore(day.getBaseDate())).toList();
        Instant openingAt = day.getBaseDate().atStartOfDay(SEOUL).toInstant().minusNanos(1_000);
        addCopies(
            copies,
            shared,
            List.copyOf(assets.values()),
            ledgerCalculator.replay(opening).positions(),
            openingAt);
      }
      for (Asset asset : closedAssets) {
        addCopies(copies, shared, List.of(asset), positions, marketClose(asset, day));
      }
    }
    if (from == null) return;
    historyRepository.saveAll(copies);
    period.includeCurrentWeekFrom(from);
  }

  private boolean marketOpen(Asset asset, MarketTradingDay day) {
    return "USD".equals(asset.getCurrency())
        ? Boolean.TRUE.equals(day.getUsTrading())
        : day.isTrading();
  }

  private Instant marketClose(Asset asset, MarketTradingDay day) {
    return "USD".equals(asset.getCurrency()) ? day.usClosesAt() : day.closesAt();
  }

  private void addCopies(
      List<SharedHoldingHistory> copies,
      SharedPortfolio portfolio,
      List<Asset> assets,
      Map<Long, Position> positions,
      Instant effectiveAt) {
    for (Asset asset : assets) {
      Position position = positions.get(asset.getId());
      copies.add(
          SharedHoldingHistory.snapshot(
              portfolio,
              asset,
              position == null ? 0 : position.quantity(),
              position == null ? BigDecimal.ZERO : position.averagePurchasePrice(),
              effectiveAt));
    }
  }
}
