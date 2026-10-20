package org.grit.daynomy.portfolio.v2.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioExchangeRateService;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
@Service
public class PortfolioTransactionServiceV2 {

  private static final List<PortfolioHoldingChangeType> BALANCE_TYPES =
      List.of(
          PortfolioHoldingChangeType.OPENING_BALANCE, PortfolioHoldingChangeType.ADJUSTED,
          PortfolioHoldingChangeType.ADDED, PortfolioHoldingChangeType.UPDATED);

  private final MemberRepository memberRepository;
  private final PortfolioRepository portfolioRepository;
  private final AssetRepository assetRepository;
  private final PortfolioHoldingRepository holdingRepository;
  private final PortfolioHoldingHistoryRepository historyRepository;
  private final PortfolioLedgerCalculator ledgerCalculator;
  private final PortfolioSnapshotServiceV2 snapshotService;
  private final PortfolioExchangeRateService exchangeRates;
  private final PlatformTransactionManager transactionManager;

  public PortfolioTransactionResponse transact(Long memberId, PortfolioTransactionRequest request) {
    var rates = exchangeRates.forMember(memberId, request.assetId(), request.tradeDate());
    return new TransactionTemplate(transactionManager)
        .execute(status -> transactInTransaction(memberId, request, rates));
  }

  private PortfolioTransactionResponse transactInTransaction(
      Long memberId, PortfolioTransactionRequest request, Map<LocalDate, BigDecimal> rates) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    Portfolio portfolio =
        portfolioRepository
            .findByMemberId(memberId)
            .map(
                saved ->
                    portfolioRepository
                        .findByIdForUpdate(saved.getId())
                        .orElseThrow(
                            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND)))
            .orElseGet(() -> portfolioRepository.save(Portfolio.create(member)));
    Asset asset =
        assetRepository
            .findById(request.assetId())
            .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
    PortfolioHoldingHistory.validateExchangeRate(asset, request.exchangeRate());
    if (historyRepository.existsByPortfolioIdAndAssetIdAndChangeTypeInAndEffectiveDateAfter(
        portfolio.getId(), asset.getId(), BALANCE_TYPES, request.tradeDate())) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_TRADE_DATE);
    }
    PortfolioHolding current =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
            .orElse(null);
    PortfolioHoldingChangeType type =
        request.type() == PortfolioTransactionType.BUY
            ? PortfolioHoldingChangeType.BUY
            : PortfolioHoldingChangeType.SELL;
    PortfolioHoldingHistory transaction =
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            type,
            current == null ? 0 : current.getQuantity(),
            current == null ? BigDecimal.ZERO.setScale(2) : current.getAveragePurchasePrice(),
            request.tradeDate(),
            request.quantity(),
            request.unitPrice(),
            request.fee(),
            request.tax());
    transaction.applyExchangeRate(request.exchangeRate());
    historyRepository.saveAndFlush(transaction);

    List<PortfolioHoldingHistory> histories =
        historyRepository.findOrderedWithAssetsByPortfolioId(portfolio.getId());
    PortfolioLedgerCalculator.LedgerResult ledger = ledgerCalculator.replay(histories);
    SavedPortfolioHoldingResponse holding = applyPosition(portfolio, asset, current, ledger);
    snapshotService.refresh(portfolio, histories, request.tradeDate(), rates);
    return new PortfolioTransactionResponse(
        transaction.getId(), holding, ledger.realizedProfitLoss());
  }

  private SavedPortfolioHoldingResponse applyPosition(
      Portfolio portfolio,
      Asset asset,
      PortfolioHolding current,
      PortfolioLedgerCalculator.LedgerResult ledger) {
    PortfolioLedgerCalculator.Position position = ledger.positions().get(asset.getId());
    if (position == null) {
      if (current != null) {
        holdingRepository.delete(current);
      }
      return null;
    }
    if (current == null) {
      return SavedPortfolioHoldingResponse.from(
          holdingRepository.save(
              new PortfolioHolding(
                  portfolio, asset, position.quantity(), position.averagePurchasePrice())));
    }
    current.change(position.quantity(), position.averagePurchasePrice());
    return SavedPortfolioHoldingResponse.from(current);
  }
}
