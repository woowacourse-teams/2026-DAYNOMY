package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PortfolioLedgerCalculator {

  private static final int MONEY_SCALE = 2;

  public LedgerResult replay(List<PortfolioHoldingHistory> histories) {
    Map<Long, Position> positions = new LinkedHashMap<>();
    BigDecimal realizedProfitLoss = BigDecimal.ZERO;

    for (PortfolioHoldingHistory history : histories) {
      Long assetId = history.getAsset().getId();
      PortfolioHoldingChangeType type = history.getChangeType();
      if (type == PortfolioHoldingChangeType.REMOVED) {
        positions.remove(assetId);
      } else if (type == PortfolioHoldingChangeType.BUY) {
        Position current = positions.get(assetId);
        long currentQuantity = current == null ? 0 : current.quantity();
        BigDecimal currentCost =
            current == null
                ? BigDecimal.ZERO
                : current.averagePurchasePrice().multiply(BigDecimal.valueOf(currentQuantity));
        long nextQuantity = Math.addExact(currentQuantity, history.getTransactionQuantity());
        BigDecimal transactionCost =
            history
                .getUnitPrice()
                .multiply(BigDecimal.valueOf(history.getTransactionQuantity()))
                .add(history.getFee())
                .add(history.getTax())
                .multiply(history.getExchangeRate());
        BigDecimal averagePrice =
            currentCost
                .add(transactionCost)
                .divide(BigDecimal.valueOf(nextQuantity), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal nativeAverage = null;
        if (current == null || current.nativeAveragePurchasePrice() != null) {
          BigDecimal nativeCost =
              current == null
                  ? BigDecimal.ZERO
                  : current
                      .nativeAveragePurchasePrice()
                      .multiply(BigDecimal.valueOf(currentQuantity));
          nativeAverage =
              nativeCost
                  .add(
                      history
                          .getUnitPrice()
                          .multiply(BigDecimal.valueOf(history.getTransactionQuantity()))
                          .add(history.getFee())
                          .add(history.getTax()))
                  .divide(BigDecimal.valueOf(nextQuantity), 4, RoundingMode.HALF_UP);
        }
        positions.put(
            assetId, new Position(history.getAsset(), nextQuantity, averagePrice, nativeAverage));
      } else if (type == PortfolioHoldingChangeType.SELL) {
        Position current = positions.get(assetId);
        if (current == null || current.quantity() < history.getTransactionQuantity()) {
          throw new BusinessException(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY);
        }
        long nextQuantity = current.quantity() - history.getTransactionQuantity();
        BigDecimal proceeds =
            history
                .getUnitPrice()
                .multiply(history.getExchangeRate())
                .subtract(current.averagePurchasePrice())
                .multiply(BigDecimal.valueOf(history.getTransactionQuantity()))
                .subtract(history.getFee().multiply(history.getExchangeRate()))
                .subtract(history.getTax().multiply(history.getExchangeRate()));
        realizedProfitLoss = realizedProfitLoss.add(proceeds);
        if (nextQuantity == 0) {
          positions.remove(assetId);
        } else {
          positions.put(
              assetId,
              new Position(
                  history.getAsset(),
                  nextQuantity,
                  current.averagePurchasePrice(),
                  current.nativeAveragePurchasePrice()));
        }
      } else {
        positions.put(
            assetId,
            new Position(
                history.getAsset(),
                history.getQuantity(),
                history.getAveragePurchasePrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                history.getNativeAveragePurchasePrice()));
      }
    }
    return new LedgerResult(
        Map.copyOf(positions), realizedProfitLoss.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
  }

  public record Position(
      Asset asset,
      long quantity,
      BigDecimal averagePurchasePrice,
      BigDecimal nativeAveragePurchasePrice) {
    public Position(Asset asset, long quantity, BigDecimal averagePurchasePrice) {
      this(asset, quantity, averagePurchasePrice, null);
    }
  }

  public record LedgerResult(Map<Long, Position> positions, BigDecimal realizedProfitLoss) {}
}
