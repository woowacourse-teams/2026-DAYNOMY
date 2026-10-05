package org.grit.daynomy.investmentcalendar.service;

import java.time.LocalDate;
import java.time.ZoneId;
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
  public List<AssetEventReaction> calculate(InvestmentEvent event, List<Long> assetIds) {
    if (assetIds.isEmpty()) {
      return List.of();
    }
    LocalDate eventDate = event.getAnnouncedAt().atZone(SEOUL).toLocalDate();
    Map<Long, List<StockDailyPrice>> pricesByAsset =
        priceRepository
            .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                assetIds,
                eventDate.minusDays(LOOKUP_RANGE_DAYS),
                eventDate.plusDays(LOOKUP_RANGE_DAYS))
            .stream()
            .collect(Collectors.groupingBy(price -> price.getAsset().getId()));

    return assetIds.stream()
        .map(
            assetId ->
                calculator
                    .calculate(
                        assetId,
                        event.getAnnouncedAt(),
                        pricesByAsset.getOrDefault(assetId, List.of()))
                    .orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
  }
}
