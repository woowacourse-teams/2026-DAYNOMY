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
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.PortfolioMarketAllocationResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PortfolioCalculationService {

  private static final int MONEY_SCALE = 2;
  private static final int PERCENTAGE_SCALE = 2;
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final StockDailyPriceRepository stockDailyPriceRepository;

  @Transactional(readOnly = true)
  public PortfolioCalculationResponse calculate(PortfolioCalculateRequest request) {
    validateNoDuplicateAssets(request.holdings());

    List<CalculatedHolding> calculatedHoldings = new ArrayList<>();
    BigDecimal totalPurchaseAmount = BigDecimal.ZERO;
    BigDecimal totalEvaluationAmount = BigDecimal.ZERO;
    LocalDate baseDate = null;

    for (PortfolioHoldingRequest holding : request.holdings()) {
      StockDailyPrice price = latestPrice(holding.assetId());
      CalculatedHolding calculated = calculateHolding(holding, price);
      calculatedHoldings.add(calculated);
      totalPurchaseAmount = totalPurchaseAmount.add(calculated.purchaseAmount());
      totalEvaluationAmount = totalEvaluationAmount.add(calculated.evaluationAmount());
      baseDate = earlierDate(baseDate, price.getBaseDate());
    }

    totalPurchaseAmount = money(totalPurchaseAmount);
    totalEvaluationAmount = money(totalEvaluationAmount);
    BigDecimal totalProfitLoss = money(totalEvaluationAmount.subtract(totalPurchaseAmount));

    return new PortfolioCalculationResponse(
        baseDate,
        totalPurchaseAmount,
        totalEvaluationAmount,
        totalProfitLoss,
        percentage(totalProfitLoss, totalPurchaseAmount),
        holdingResponses(calculatedHoldings, totalEvaluationAmount),
        marketAllocations(calculatedHoldings, totalEvaluationAmount));
  }

  private void validateNoDuplicateAssets(List<PortfolioHoldingRequest> holdings) {
    Set<Long> assetIds = new HashSet<>();
    boolean duplicated =
        holdings.stream().map(PortfolioHoldingRequest::assetId).anyMatch(id -> !assetIds.add(id));
    if (duplicated) {
      throw new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    }
  }

  private StockDailyPrice latestPrice(Long assetId) {
    return stockDailyPriceRepository
        .findFirstByAssetIdOrderByBaseDateDesc(assetId)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.STOCK_PRICE_NOT_FOUND));
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
                    holding.asset().getMarket(),
                    holding.baseDate(),
                    holding.quantity(),
                    holding.averagePurchasePrice(),
                    holding.closePrice(),
                    holding.purchaseAmount(),
                    holding.evaluationAmount(),
                    holding.profitLoss(),
                    holding.returnRate(),
                    percentage(holding.evaluationAmount(), totalEvaluationAmount)))
        .toList();
  }

  private List<PortfolioMarketAllocationResponse> marketAllocations(
      List<CalculatedHolding> holdings, BigDecimal totalEvaluationAmount) {
    Map<StockMarket, BigDecimal> amountsByMarket = new EnumMap<>(StockMarket.class);
    for (CalculatedHolding holding : holdings) {
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
