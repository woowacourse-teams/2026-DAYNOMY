package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class LeagueReturnService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final BigDecimal MINIMUM_EVALUATION = new BigDecimal("100000");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final InvestorProfileRepository profileRepository;
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingHistoryRepository historyRepository;
  private final StockDailyPriceRepository priceRepository;
  private final PortfolioDailyReturnRepository returnRepository;

  @Transactional
  public int recalculate() {
    List<LocalDate> dates = priceRepository.findDistinctBaseDatesOrderByBaseDate();
    if (dates.size() < 2) {
      return 0;
    }
    int calculated = 0;
    for (InvestorProfile profile :
        profileRepository.findAllByProfilePublicTrueAndLeagueEnabledTrue()) {
      SharedPortfolio portfolio =
          portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
      if (portfolio == null) {
        continue;
      }
      LocalDate enabledOn = profile.getLeagueEnabledAt().atZone(SEOUL).toLocalDate();
      LocalDate eligibleFrom = enabledOn.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
      for (int index = 1; index < dates.size(); index++) {
        LocalDate baseDate = dates.get(index);
        if (baseDate.isBefore(eligibleFrom)) {
          continue;
        }
        calculate(portfolio, baseDate, dates.get(index - 1));
        calculated++;
      }
    }
    return calculated;
  }

  private void calculate(
      SharedPortfolio portfolio, LocalDate baseDate, LocalDate previousBaseDate) {
    PortfolioDailyReturn dailyReturn =
        returnRepository
            .findByPortfolioIdAndBaseDateAndCalculationVersion(portfolio.getId(), baseDate, 1)
            .orElseGet(() -> new PortfolioDailyReturn(portfolio, baseDate));
    Instant calculatedAt = Instant.now();
    List<HoldingAtClose> holdings = reconstructHoldings(portfolio.getId(), baseDate);
    if (holdings.isEmpty()) {
      dailyReturn.exclude("NO_HOLDINGS", calculatedAt);
      returnRepository.save(dailyReturn);
      return;
    }

    BigDecimal startingAmount = BigDecimal.ZERO;
    BigDecimal endingAmount = BigDecimal.ZERO;
    BigDecimal largestPosition = BigDecimal.ZERO;
    for (HoldingAtClose holding : holdings) {
      if (!isSupported(holding.asset())) {
        dailyReturn.exclude("UNSUPPORTED_ASSET", calculatedAt);
        returnRepository.save(dailyReturn);
        return;
      }
      StockDailyPrice previousPrice = findPrice(holding.asset().getId(), previousBaseDate);
      StockDailyPrice currentPrice = findPrice(holding.asset().getId(), baseDate);
      if (previousPrice == null || currentPrice == null) {
        dailyReturn.exclude("MISSING_PRICE", calculatedAt);
        returnRepository.save(dailyReturn);
        return;
      }
      BigDecimal quantity = BigDecimal.valueOf(holding.quantity());
      BigDecimal startingPosition = previousPrice.getClosePrice().multiply(quantity);
      startingAmount = startingAmount.add(startingPosition);
      endingAmount = endingAmount.add(currentPrice.getClosePrice().multiply(quantity));
      largestPosition = largestPosition.max(startingPosition);
    }

    if (startingAmount.compareTo(MINIMUM_EVALUATION) < 0) {
      dailyReturn.exclude("MINIMUM_EVALUATION", calculatedAt);
      returnRepository.save(dailyReturn);
      return;
    }
    BigDecimal dailyRate =
        endingAmount
            .subtract(startingAmount)
            .multiply(HUNDRED)
            .divide(startingAmount, 4, RoundingMode.HALF_UP);
    BigDecimal maxWeight =
        largestPosition.multiply(HUNDRED).divide(startingAmount, 2, RoundingMode.HALF_UP);
    dailyReturn.complete(
        dailyRate,
        startingAmount.setScale(2, RoundingMode.HALF_UP),
        endingAmount.setScale(2, RoundingMode.HALF_UP),
        maxWeight,
        calculatedAt);
    returnRepository.save(dailyReturn);
  }

  private List<HoldingAtClose> reconstructHoldings(Long portfolioId, LocalDate baseDate) {
    Map<Long, HoldingAtClose> holdings = new LinkedHashMap<>();
    List<SharedHoldingHistory> histories =
        historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            portfolioId, baseDate.atStartOfDay(SEOUL).toInstant());
    for (SharedHoldingHistory history : histories) {
      Long assetId = history.getAsset().getId();
      if (history.getChangeType() == ChangeType.REMOVED) {
        holdings.remove(assetId);
      } else {
        holdings.put(assetId, new HoldingAtClose(history.getAsset(), history.getQuantity()));
      }
    }
    return List.copyOf(holdings.values());
  }

  private StockDailyPrice findPrice(Long assetId, LocalDate baseDate) {
    return priceRepository.findByAssetIdAndBaseDate(assetId, baseDate).orElse(null);
  }

  private boolean isSupported(Asset asset) {
    return asset.isListed()
        && (asset.getCategory() == AssetCategory.STOCK || asset.getCategory() == AssetCategory.ETF);
  }

  private record HoldingAtClose(Asset asset, long quantity) {}
}
