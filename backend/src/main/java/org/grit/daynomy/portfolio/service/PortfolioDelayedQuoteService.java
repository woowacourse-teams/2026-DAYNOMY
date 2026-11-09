package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;
import org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.grit.daynomy.portfolio.repository.PortfolioDelayedQuoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(name = "portfolio.delayed-quotes.enabled", havingValue = "true")
public class PortfolioDelayedQuoteService {

  private static final int BATCH_SIZE = 20;
  private static final int BASE_CALLS_PER_SLOT = 300;
  private static final int MAX_CALLS_PER_SLOT = 600;
  private static final int DAILY_LIMIT = 50_000;
  private static final int SLOT_SECONDS = 300;
  private static final Duration CLOSING_GRACE = Duration.ofMinutes(20);

  private final AssetRepository assets;
  private final PortfolioDelayedQuoteRepository quotes;
  private final UsdKrwRateRepository rates;
  private final MarketTradingDayRepository tradingDays;
  private final KrxCalendarClient krxCalendar;
  private final EodhdClient client;
  private final Clock clock;
  private final Duration maxAge;
  private CalendarEntry krCalendar;

  public PortfolioDelayedQuoteService(
      AssetRepository assets,
      PortfolioDelayedQuoteRepository quotes,
      UsdKrwRateRepository rates,
      MarketTradingDayRepository tradingDays,
      KrxCalendarClient krxCalendar,
      EodhdClient client,
      Clock clock,
      @Value("${portfolio.delayed-quotes.max-age:30m}") Duration maxAge) {
    if (maxAge.isZero() || maxAge.isNegative()) {
      throw new IllegalArgumentException("Delayed quote max age must be positive");
    }
    this.assets = assets;
    this.quotes = quotes;
    this.rates = rates;
    this.tradingDays = tradingDays;
    this.krxCalendar = krxCalendar;
    this.client = client;
    this.clock = clock;
    this.maxAge = maxAge;
  }

  public Map<Long, ValuationQuote> requestQuotes(List<Long> ids) {
    try {
      return readAndRequestQuotes(ids);
    } catch (DataAccessException exception) {
      log.warn("Delayed quote cache unavailable; using confirmed closes");
      return Map.of();
    }
  }

  private Map<Long, ValuationQuote> readAndRequestQuotes(List<Long> ids) {
    Instant now = clock.instant();
    Map<String, Asset> bySymbol = new HashMap<>();
    for (Asset asset : assets.findAllById(ids)) {
      if (asset.isListed()
          && asset.getMarket() != null
          && (asset.getCategory() == AssetCategory.STOCK
              || asset.getCategory() == AssetCategory.ETF)) {
        bySymbol.put(symbol(asset), asset);
      }
    }
    Map<Long, ValuationQuote> result = new HashMap<>();
    Map<LocalDate, Optional<BigDecimal>> usdRates = new HashMap<>();
    for (var quote : quotes.request(List.copyOf(bySymbol.keySet()), now)) {
      Asset asset = bySymbol.get(quote.symbol());
      Market market = asset.getMarket().isUs() ? Market.US : Market.KR;
      if (!isSameMarketDay(quote.asOf(), now, market)) continue;
      LocalDate date = quote.asOf().atZone(market.zone).toLocalDate();
      BigDecimal krwPrice = quote.price();
      if (market == Market.US) {
        var rate = usdRates.computeIfAbsent(date, this::storedUsdRate);
        if (rate.isEmpty()) continue;
        krwPrice = krwPrice.multiply(rate.orElseThrow());
      }
      result.put(
          asset.getId(),
          new ValuationQuote(
              asset,
              date,
              quote.asOf(),
              quote.price(),
              krwPrice,
              quote.asOf().isBefore(now.minus(maxAge))));
    }
    return result;
  }

  @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
  public void refresh() {
    for (Market market : Market.values()) refreshMarket(market);
  }

  private void refreshMarket(Market market) {
    Instant now = clock.instant();
    var local = now.atZone(market.zone);
    if (local.getDayOfWeek().getValue() > 5) return;
    Optional<Session> current = session(market, local.toLocalDate(), now);
    if (current.isEmpty()) return;
    Session session = current.orElseThrow();
    if (now.isBefore(session.opens()) || now.isAfter(session.closes().plus(CLOSING_GRACE))) return;
    Instant slot = Instant.ofEpochSecond(now.getEpochSecond() / SLOT_SECONDS * SLOT_SECONDS);
    long secondsUntilClose =
        Duration.between(slot, session.closes().plus(CLOSING_GRACE)).toSeconds();
    int futureSlots = Math.toIntExact(secondsUntilClose / SLOT_SECONDS);
    int protectedCalls = futureSlots * BASE_CALLS_PER_SLOT;
    // Korean trading ends before US trading begins on the same UTC budget date.
    if (market == Market.KR
        && UsQuoteCalendar.closeOn(slot.atOffset(ZoneOffset.UTC).toLocalDate()).isPresent()) {
      protectedCalls += DAILY_LIMIT / 2;
    }
    List<String> symbols =
        quotes.reserve(market.name(), slot, now, MAX_CALLS_PER_SLOT, DAILY_LIMIT, protectedCalls);
    for (int offset = 0; offset < symbols.size(); offset += BATCH_SIZE) {
      Instant attemptedAt = clock.instant();
      if (!attemptedAt.isBefore(slot.plusSeconds(SLOT_SECONDS))) return;
      var batch = symbols.subList(offset, Math.min(offset + BATCH_SIZE, symbols.size()));
      quotes.markAttempted(batch, attemptedAt);
      try {
        var response = client.getDelayedQuotes(batch);
        if (response.status() == 402) {
          quotes.exhaust(now);
          log.warn("Delayed quote provider daily limit reached for {}", market);
          return;
        }
        if (response.status() == 429) {
          log.warn("Delayed quote provider rate limited {}", market);
          return;
        }
        if (response.status() != 200) {
          log.warn("Delayed quote request returned {} for {}", response.status(), market);
          continue;
        }
        Instant receivedAt = clock.instant();
        for (var quote : response.quotes()) {
          if (batch.contains(quote.symbol())
              && isSameMarketDay(quote.asOf(), receivedAt, market)
              && !quote.asOf().isBefore(receivedAt.minus(maxAge))
              && !quote.asOf().isBefore(session.opens())
              && !quote.asOf().isAfter(session.closes())) {
            quotes.save(quote.symbol(), quote.price(), quote.asOf());
          }
        }
      } catch (BusinessException exception) {
        log.warn("Delayed quote request failed for {}", market);
        return;
      }
    }
  }

  private Optional<Session> session(Market market, LocalDate date, Instant now) {
    if (market == Market.US) {
      return UsQuoteCalendar.closeOn(date)
          .map(close -> session(market, date, LocalTime.of(9, 30), close));
    }

    if (krCalendar != null
        && krCalendar.date().equals(date)
        && now.isBefore(krCalendar.expiresAt())) {
      return krCalendar.session();
    }
    try {
      boolean trading =
          tradingDays
              .findById(date)
              .map(MarketTradingDay::isTrading)
              .orElseGet(() -> !krxCalendar.holidays(date.getYear()).contains(date));
      Optional<Session> session =
          trading
              ? Optional.of(session(market, date, LocalTime.of(9, 0), LocalTime.of(15, 30)))
              : Optional.empty();
      krCalendar =
          new CalendarEntry(date, date.plusDays(1).atStartOfDay(market.zone).toInstant(), session);
      return session;
    } catch (BusinessException exception) {
      log.warn("Delayed quote session unavailable for KR");
      krCalendar = new CalendarEntry(date, now.plus(Duration.ofHours(1)), Optional.empty());
      return Optional.empty();
    }
  }

  private Session session(Market market, LocalDate date, LocalTime open, LocalTime close) {
    return new Session(
        date.atTime(open).atZone(market.zone).toInstant(),
        date.atTime(close).atZone(market.zone).toInstant());
  }

  private boolean isSameMarketDay(Instant asOf, Instant now, Market market) {
    return !asOf.isAfter(now)
        && asOf.atZone(market.zone).toLocalDate().equals(now.atZone(market.zone).toLocalDate());
  }

  private Optional<BigDecimal> storedUsdRate(LocalDate date) {
    return rates
        .findFirstByBaseDateBetweenOrderByBaseDateDescFetchedAtDesc(date.minusDays(14), date)
        .map(UsdKrwRate::getRate);
  }

  private static String symbol(Asset asset) {
    if (asset.getMarket().isUs()) return asset.getAssetCode();
    return asset.getAssetCode() + (asset.getMarket() == StockMarket.KOSDAQ ? ".KQ" : ".KO");
  }

  public record ValuationQuote(
      Asset asset,
      LocalDate date,
      Instant asOf,
      BigDecimal price,
      BigDecimal krwPrice,
      boolean refreshPending) {}

  private record Session(Instant opens, Instant closes) {}

  private record CalendarEntry(LocalDate date, Instant expiresAt, Optional<Session> session) {}

  private enum Market {
    KR("Asia/Seoul"),
    US("America/New_York");

    private final ZoneId zone;

    Market(String zone) {
      this.zone = ZoneId.of(zone);
    }
  }
}
