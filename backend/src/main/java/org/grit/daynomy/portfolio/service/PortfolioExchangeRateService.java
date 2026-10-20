package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class PortfolioExchangeRateService {
  private final StockDailyPriceRepository prices;
  private final PortfolioHoldingHistoryRepository histories;
  private final ExchangeRateService exchangeRates;

  public Map<LocalDate, BigDecimal> forMember(
      Long memberId, Long additionalAssetId, LocalDate fromDate) {
    var ids = new HashSet<>(histories.findAssetIdsByMemberId(memberId));
    if (additionalAssetId != null) ids.add(additionalAssetId);
    return load(ids, null, fromDate);
  }

  public Map<LocalDate, BigDecimal> forTransaction(
      Long memberId, Long transactionId, LocalDate newDate) {
    var oldDate = histories.findEffectiveDateByIdAndMemberId(transactionId, memberId);
    if (oldDate.isEmpty()) return Map.of();
    LocalDate from = newDate != null && newDate.isBefore(oldDate.get()) ? newDate : oldDate.get();
    return forMember(memberId, null, from);
  }

  public Map<LocalDate, BigDecimal> forSnapshot(
      Long portfolioId, LocalDate date, LocalDate previous) {
    return load(
        new HashSet<>(histories.findAssetIdsByPortfolioId(portfolioId)),
        previous == null ? Set.of(date) : Set.of(date, previous),
        null);
  }

  private Map<LocalDate, BigDecimal> load(
      Set<Long> assetIds, Set<LocalDate> selectedDates, LocalDate fromDate) {
    if (assetIds.isEmpty()) return Map.of();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    if (fromDate != null && !fromDate.isBefore(today)) return Map.of();
    var dates =
        prices
            .findBaseDatesByAssetIdsAndMarkets(List.copyOf(assetIds), StockMarket.usMarkets())
            .stream()
            .filter(date -> date.isBefore(today))
            .filter(date -> selectedDates == null || selectedDates.contains(date))
            .toList();
    if (fromDate != null) {
      var previous =
          dates.stream()
              .filter(date -> date.isBefore(fromDate))
              .max(LocalDate::compareTo)
              .orElse(null);
      dates =
          dates.stream().filter(date -> !date.isBefore(fromDate) || date.equals(previous)).toList();
    }
    return exchangeRates.ratesForDates(dates);
  }
}
