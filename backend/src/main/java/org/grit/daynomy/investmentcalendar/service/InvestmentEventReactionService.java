package org.grit.daynomy.investmentcalendar.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.investmentcalendar.domain.AssetEventReaction;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.TradingDayReactionCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestmentEventReactionService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int LOOKUP_RANGE_DAYS = 10;

  private final StockDailyPriceRepository priceRepository;
  private final TradingDayReactionCalculator calculator = new TradingDayReactionCalculator();

  @Transactional(readOnly = true)
  public Map<InvestmentEvent, List<AssetEventReaction>> calculate(
      List<InvestmentEvent> events, List<Long> assetIds) {
    if (assetIds.isEmpty() || events.isEmpty()) {
      return Map.of();
    }
    LocalDate from =
        events.stream()
            .map(this::eventDate)
            .min(Comparator.naturalOrder())
            .orElseThrow()
            .minusDays(LOOKUP_RANGE_DAYS);
    LocalDate to =
        events.stream()
            .map(this::eventDate)
            .max(Comparator.naturalOrder())
            .orElseThrow()
            .plusDays(LOOKUP_RANGE_DAYS);
    Map<Long, List<StockDailyPrice>> pricesByAsset =
        priceRepository
            .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(assetIds, from, to)
            .stream()
            .collect(Collectors.groupingBy(price -> price.getAsset().getId()));
    Map<InvestmentEvent, List<AssetEventReaction>> result = new HashMap<>();
    for (InvestmentEvent event : events) {
      LocalDate date = eventDate(event);
      List<AssetEventReaction> reactions =
          assetIds.stream()
              .map(
                  assetId ->
                      calculator
                          .calculate(
                              assetId,
                              event.getAnnouncedAt(),
                              pricesInWindow(pricesByAsset.getOrDefault(assetId, List.of()), date))
                          .orElse(null))
              .filter(java.util.Objects::nonNull)
              .toList();
      result.put(event, reactions);
    }
    return result;
  }

  private List<StockDailyPrice> pricesInWindow(List<StockDailyPrice> prices, LocalDate date) {
    LocalDate from = date.minusDays(LOOKUP_RANGE_DAYS);
    LocalDate to = date.plusDays(LOOKUP_RANGE_DAYS);
    return prices.stream()
        .filter(price -> !price.getBaseDate().isBefore(from) && !price.getBaseDate().isAfter(to))
        .toList();
  }

  private LocalDate eventDate(InvestmentEvent event) {
    return event.getAnnouncedAt().atZone(SEOUL).toLocalDate();
  }
}
