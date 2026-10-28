package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
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
    var histories =
        originalHistoryRepository.findOrderedWithAssetsByPortfolioId(original.getId()).stream()
            .filter(h -> assetIds.contains(h.getAsset().getId()))
            .toList();
    if (!histories.stream()
        .map(h -> h.getAsset().getId())
        .collect(Collectors.toSet())
        .containsAll(assetIds)) return;
    var days =
        tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, joined).stream()
            .filter(d -> d.isTrading() && d.closesAt().isBefore(period.getStartedAt()))
            .toList();
    List<SharedHoldingHistory> copies = new ArrayList<>();
    Instant from = null;
    for (var day : days) {
      var source =
          histories.stream().filter(h -> !h.getEffectiveDate().isAfter(day.getBaseDate())).toList();
      var positions = ledgerCalculator.replay(source).positions();
      if (from == null) {
        if (positions.isEmpty()) continue;
        from = day.closesAt();
        var opening =
            source.stream().filter(h -> h.getEffectiveDate().isBefore(day.getBaseDate())).toList();
        Instant openingAt = day.getBaseDate().atStartOfDay(SEOUL).toInstant().minusNanos(1_000);
        addCopies(copies, holdings, ledgerCalculator.replay(opening).positions(), openingAt);
      }
      addCopies(copies, holdings, positions, day.closesAt());
    }
    if (from == null) return;
    historyRepository.saveAll(copies);
    period.includeCurrentWeekFrom(from);
  }

  private void addCopies(
      List<SharedHoldingHistory> copies,
      List<SharedHolding> holdings,
      Map<Long, Position> positions,
      Instant effectiveAt) {
    for (var holding : holdings) {
      Position position = positions.get(holding.getAsset().getId());
      copies.add(
          SharedHoldingHistory.snapshot(
              holding.getPortfolio(),
              holding.getAsset(),
              position == null ? 0 : position.quantity(),
              position == null ? BigDecimal.ZERO : position.averagePurchasePrice(),
              effectiveAt));
    }
  }
}
