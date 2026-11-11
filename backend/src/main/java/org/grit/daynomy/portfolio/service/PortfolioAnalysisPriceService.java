package org.grit.daynomy.portfolio.service;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext.Observation;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext.Status;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisPriceService {
  private final AssetRepository assets;
  private final StockDailyPriceRepository prices;
  private final ObjectProvider<PortfolioDelayedQuoteService> delayedQuotes;
  private final Clock clock;

  /** 개인 보유 정보 없이 기존 공통 가격 조회와 종가 이력을 재사용한다. */
  public Map<String, PortfolioAnalysisPriceContext> lookup(List<String> names) {
    if (names.isEmpty()) return Map.of();
    try {
      return read(names);
    } catch (DataAccessException unavailable) {
      log.warn("분석 가격 자료 조회 실패: targetCount={}", names.size());
      return names.stream()
          .collect(
              Collectors.toMap(
                  name -> name,
                  name -> PortfolioAnalysisPriceContext.unavailable(Status.LOOKUP_FAILED)));
    }
  }

  private Map<String, PortfolioAnalysisPriceContext> read(List<String> names) {
    Map<String, PortfolioAnalysisPriceContext> result = new LinkedHashMap<>();
    Map<String, Asset> resolved = new LinkedHashMap<>();
    for (String name : names) {
      String keyword = name.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
      var candidates =
          assets.searchListedSecurities(
              keyword,
              Set.of(AssetCategory.STOCK, AssetCategory.ETF),
              Set.of(StockMarket.values()),
              PageRequest.of(0, 50));
      var matches =
          candidates.stream()
              .filter(
                  asset ->
                      normalize(asset.getName()).equals(normalize(name))
                          || normalize(asset.getNameKo()).equals(normalize(name)))
              .toList();
      if (matches.size() != 1 || matches.getFirst().getMarket() == null) {
        result.put(
            name,
            PortfolioAnalysisPriceContext.unavailable(
                matches.size() > 1 ? Status.AMBIGUOUS_ASSET : Status.UNAVAILABLE));
      } else resolved.put(name, matches.getFirst());
    }
    if (resolved.isEmpty()) return result;
    var now = clock.instant();
    var today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
    var ids = resolved.values().stream().map(Asset::getId).distinct().toList();
    var history =
        prices.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            ids, today.minusDays(31), today);
    var latest = prices.findLatestTwoByAssetIds(ids);
    Map<Long, PortfolioDelayedQuoteService.ValuationQuote> cached;
    try {
      var service = delayedQuotes.getIfAvailable();
      cached = service == null ? Map.of() : service.requestQuotes(ids);
    } catch (DataAccessException unavailable) {
      log.warn("저장 지연 시세 조회 실패로 확정 종가를 사용합니다.");
      cached = Map.of();
    }
    for (var entry : resolved.entrySet()) {
      var asset = entry.getValue();
      var localToday =
          now.atZone(ZoneId.of(asset.getMarket().isUs() ? "America/New_York" : "Asia/Seoul"))
              .toLocalDate();
      List<Observation> observations = new ArrayList<>();
      java.util.stream.Stream.concat(history.stream(), latest.stream())
          .filter(price -> price.getAsset().getId().equals(asset.getId()))
          .forEach(
              price ->
                  observations.add(
                      new Observation(price.getBaseDate(), null, price.getClosePrice(), "CLOSE")));
      var closeDate =
          observations.stream()
              .map(Observation::date)
              .filter(date -> !date.isAfter(localToday))
              .max(java.util.Comparator.naturalOrder())
              .orElse(null);
      var quote = cached.get(asset.getId());
      if (quote != null
          && !quote.asOf().isAfter(now)
          && !quote.asOf().isBefore(now.minus(Duration.ofMinutes(30)))) {
        var quoteDate =
            quote
                .asOf()
                .atZone(ZoneId.of(asset.getMarket().isUs() ? "America/New_York" : "Asia/Seoul"))
                .toLocalDate();
        if (closeDate == null || quoteDate.isAfter(closeDate))
          observations.add(new Observation(quoteDate, quote.asOf(), quote.price(), "DELAYED"));
      }
      result.put(
          entry.getKey(),
          PortfolioAnalysisPriceContext.calculate(
              asset.getCurrency(), observations, localToday.minusDays(30), localToday, now));
    }
    return result;
  }

  private String normalize(String name) {
    return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
  }
}
