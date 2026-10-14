package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.PortfolioMarketAllocationResponse;
import org.grit.daynomy.portfolio.dto.PortfolioPricingStatus;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
@Service
public class PortfolioCalculationService {

  private static final int MONEY_SCALE = 2;
  private static final int PERCENTAGE_SCALE = 2;
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final StockDailyPriceRepository stockDailyPriceRepository;
  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public PortfolioCalculationResponse calculate(PortfolioCalculateRequest request) {
    validateNoDuplicateAssets(request.holdings());
    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.PORTFOLIO_CALCULATION_STARTED.code())
        .addKeyValue("holdingCount", request.holdings().size())
        .log(LogEvent.PORTFOLIO_CALCULATION_STARTED.message());

    List<CalculatedHolding> calculatedHoldings = new ArrayList<>();
    BigDecimal totalPurchaseAmount = BigDecimal.ZERO;
    BigDecimal totalEvaluationAmount = BigDecimal.ZERO;
    BigDecimal previousEvaluationAmount = BigDecimal.ZERO;
    LocalDate baseDate = null;
    Set<LocalDate> currentPriceDates = new HashSet<>();
    Set<LocalDate> previousPriceDates = new HashSet<>();
    boolean allHoldingsHavePreviousPrice = true;
    int unpricedHoldingCount = 0;

    Map<Long, List<StockDailyPrice>> pricesByAssetId = latestPrices(request.holdings());
    for (PortfolioHoldingRequest holding : request.holdings()) {
      List<StockDailyPrice> prices = pricesByAssetId.getOrDefault(holding.assetId(), List.of());
      if (prices.isEmpty()) {
        Asset asset =
            assetRepository
                .findById(holding.assetId())
                .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
        calculatedHoldings.add(calculateUnpricedHolding(holding, asset));
        totalPurchaseAmount =
            totalPurchaseAmount.add(
                holding.averagePurchasePrice().multiply(BigDecimal.valueOf(holding.quantity())));
        unpricedHoldingCount++;
        allHoldingsHavePreviousPrice = false;
        continue;
      }
      StockDailyPrice price = prices.getFirst();
      CalculatedHolding calculated = calculateHolding(holding, price);
      calculatedHoldings.add(calculated);
      totalPurchaseAmount = totalPurchaseAmount.add(calculated.purchaseAmount());
      totalEvaluationAmount = totalEvaluationAmount.add(calculated.evaluationAmount());
      baseDate = earlierDate(baseDate, price.getBaseDate());
      currentPriceDates.add(price.getBaseDate());

      if (prices.size() < 2 || !prices.get(1).getBaseDate().isBefore(price.getBaseDate())) {
        allHoldingsHavePreviousPrice = false;
      } else {
        StockDailyPrice previousPrice = prices.get(1);
        previousPriceDates.add(previousPrice.getBaseDate());
        previousEvaluationAmount =
            previousEvaluationAmount.add(
                previousPrice.getClosePrice().multiply(BigDecimal.valueOf(holding.quantity())));
      }
    }

    totalPurchaseAmount = money(totalPurchaseAmount);
    totalEvaluationAmount = money(totalEvaluationAmount);
    boolean pricesComplete = unpricedHoldingCount == 0;
    BigDecimal totalProfitLoss =
        pricesComplete ? money(totalEvaluationAmount.subtract(totalPurchaseAmount)) : null;
    BigDecimal dailyProfitLoss = null;
    BigDecimal dailyReturnRate = null;
    boolean canCalculateDailyPerformance =
        pricesComplete
            && allHoldingsHavePreviousPrice
            && currentPriceDates.size() == 1
            && previousPriceDates.size() == 1;
    if (canCalculateDailyPerformance) {
      previousEvaluationAmount = money(previousEvaluationAmount);
      dailyProfitLoss = money(totalEvaluationAmount.subtract(previousEvaluationAmount));
      dailyReturnRate = percentage(dailyProfitLoss, previousEvaluationAmount);
    }

    PortfolioCalculationResponse response =
        new PortfolioCalculationResponse(
            baseDate,
            pricingStatus(unpricedHoldingCount, currentPriceDates),
            calculatedHoldings.size() - unpricedHoldingCount,
            unpricedHoldingCount,
            totalPurchaseAmount,
            totalEvaluationAmount,
            dailyProfitLoss,
            dailyReturnRate,
            totalProfitLoss,
            totalProfitLoss == null ? null : percentage(totalProfitLoss, totalPurchaseAmount),
            holdingResponses(calculatedHoldings, totalEvaluationAmount),
            marketAllocations(calculatedHoldings, totalEvaluationAmount));

    log.atInfo()
        .addKeyValue("event", LogEvent.PORTFOLIO_CALCULATION_COMPLETED.code())
        .addKeyValue("holdingCount", calculatedHoldings.size())
        .addKeyValue("marketCount", response.marketAllocations().size())
        .addKeyValue("baseDate", response.baseDate())
        .addKeyValue("durationMs", elapsedMillis(startedAt))
        .log(LogEvent.PORTFOLIO_CALCULATION_COMPLETED.message());

    return response;
  }

  private void validateNoDuplicateAssets(List<PortfolioHoldingRequest> holdings) {
    Set<Long> assetIds = new HashSet<>();
    boolean duplicated =
        holdings.stream().map(PortfolioHoldingRequest::assetId).anyMatch(id -> !assetIds.add(id));
    if (duplicated) {
      throw new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    }
  }

  private Map<Long, List<StockDailyPrice>> latestPrices(List<PortfolioHoldingRequest> holdings) {
    if (holdings.isEmpty()) {
      return Map.of();
    }
    List<Long> assetIds = holdings.stream().map(PortfolioHoldingRequest::assetId).toList();
    return stockDailyPriceRepository.findLatestTwoByAssetIds(assetIds).stream()
        .collect(Collectors.groupingBy(price -> price.getAsset().getId()));
  }

  private CalculatedHolding calculateUnpricedHolding(PortfolioHoldingRequest holding, Asset asset) {
    BigDecimal purchaseAmount =
        money(holding.averagePurchasePrice().multiply(BigDecimal.valueOf(holding.quantity())));
    return new CalculatedHolding(
        asset,
        null,
        holding.quantity(),
        money(holding.averagePurchasePrice()),
        null,
        purchaseAmount,
        null,
        null,
        null);
  }

  private CalculatedHolding calculateHolding(
      PortfolioHoldingRequest holding, StockDailyPrice price) {
    BigDecimal quantity = BigDecimal.valueOf(holding.quantity());
    BigDecimal purchaseAmount = money(holding.averagePurchasePrice().multiply(quantity));
    BigDecimal evaluationAmount = money(price.getClosePrice().multiply(quantity));
    BigDecimal profitLoss = money(evaluationAmount.subtract(purchaseAmount));
    return new CalculatedHolding(
        price.getAsset(),
        price.getBaseDate(),
        holding.quantity(),
        money(holding.averagePurchasePrice()),
        money(price.getClosePrice()),
        purchaseAmount,
        evaluationAmount,
        profitLoss,
        percentage(profitLoss, purchaseAmount));
  }

  private List<PortfolioHoldingResponse> holdingResponses(
      List<CalculatedHolding> holdings, BigDecimal totalEvaluationAmount) {
    return holdings.stream()
        .map(
            holding ->
                new PortfolioHoldingResponse(
                    holding.asset().getId(),
                    holding.asset().getAssetCode(),
                    holding.asset().getName(),
                    holding.asset().getCategory(),
                    holding.asset().getMarket(),
                    holding.closePrice() != null,
                    holding.baseDate(),
                    holding.quantity(),
                    holding.averagePurchasePrice(),
                    holding.closePrice(),
                    holding.purchaseAmount(),
                    holding.evaluationAmount(),
                    holding.profitLoss(),
                    holding.returnRate(),
                    holding.evaluationAmount() == null
                        ? null
                        : percentage(holding.evaluationAmount(), totalEvaluationAmount)))
        .toList();
  }

  private List<PortfolioMarketAllocationResponse> marketAllocations(
      List<CalculatedHolding> holdings, BigDecimal totalEvaluationAmount) {
    Map<StockMarket, BigDecimal> amountsByMarket = new EnumMap<>(StockMarket.class);
    for (CalculatedHolding holding : holdings) {
      if (holding.evaluationAmount() == null) {
        continue;
      }
      amountsByMarket.merge(
          holding.asset().getMarket(), holding.evaluationAmount(), BigDecimal::add);
    }

    return amountsByMarket.entrySet().stream()
        .map(
            entry ->
                new PortfolioMarketAllocationResponse(
                    entry.getKey(),
                    money(entry.getValue()),
                    percentage(entry.getValue(), totalEvaluationAmount)))
        .toList();
  }

  private PortfolioPricingStatus pricingStatus(
      int unpricedHoldingCount, Set<LocalDate> currentPriceDates) {
    if (unpricedHoldingCount > 0) {
      return PortfolioPricingStatus.PARTIAL;
    }
    return currentPriceDates.size() > 1
        ? PortfolioPricingStatus.MIXED_DATES
        : PortfolioPricingStatus.COMPLETE;
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal percentage(BigDecimal amount, BigDecimal total) {
    if (total.signum() == 0) {
      return BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }
    return amount.multiply(ONE_HUNDRED).divide(total, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
  }

  private LocalDate earlierDate(LocalDate current, LocalDate candidate) {
    return current == null || candidate.isBefore(current) ? candidate : current;
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private record CalculatedHolding(
      Asset asset,
      LocalDate baseDate,
      long quantity,
      BigDecimal averagePurchasePrice,
      BigDecimal closePrice,
      BigDecimal purchaseAmount,
      BigDecimal evaluationAmount,
      BigDecimal profitLoss,
      BigDecimal returnRate) {}
}
