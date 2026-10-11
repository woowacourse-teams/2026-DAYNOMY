package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
import org.grit.daynomy.portfolio.dto.PortfolioHoldingHistoryResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingCreateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SavedPortfolioService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final PortfolioRepository portfolioRepository;
  private final PortfolioHoldingRepository holdingRepository;
  private final PortfolioHoldingHistoryRepository historyRepository;
  private final MemberRepository memberRepository;
  private final AssetRepository assetRepository;
  private final PortfolioLedgerCalculator ledgerCalculator;
  private final PortfolioSnapshotTransactionService snapshotTransactionService;

  @Transactional(readOnly = true)
  public SavedPortfolioResponse get(Long memberId) {
    return portfolioRepository
        .findByMemberId(memberId)
        .map(
            portfolio -> {
              PortfolioLedgerCalculator.LedgerResult ledger = replay(portfolio.getId());
              return new SavedPortfolioResponse(
                  holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()).stream()
                      .map(SavedPortfolioHoldingResponse::from)
                      .toList(),
                  ledger.realizedProfitLoss());
            })
        .orElseGet(SavedPortfolioResponse::empty);
  }

  @Transactional
  public SavedPortfolioHoldingResponse add(
      Long memberId, SavedPortfolioHoldingCreateRequest request) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    Portfolio portfolio = getOrCreatePortfolio(member);
    if (holdingRepository.existsByPortfolioIdAndAssetId(portfolio.getId(), request.assetId())) {
      throw new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    }
    Asset asset =
        assetRepository
            .findById(request.assetId())
            .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
    PortfolioHolding holding =
        holdingRepository.save(
            new PortfolioHolding(
                portfolio, asset, request.quantity(), request.averagePurchasePrice()));
    historyRepository.save(
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.OPENING_BALANCE,
            holding.getQuantity(),
            holding.getAveragePurchasePrice(),
            LocalDate.now(SEOUL),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    return SavedPortfolioHoldingResponse.from(holding);
  }

  @Transactional
  public SavedPortfolioHoldingResponse update(
      Long memberId, Long assetId, SavedPortfolioHoldingUpdateRequest request) {
    Portfolio portfolio = getPortfolio(memberId);
    PortfolioHolding holding = getHolding(portfolio.getId(), assetId);
    holding.change(request.quantity(), request.averagePurchasePrice());
    historyRepository.save(
        new PortfolioHoldingHistory(
            portfolio,
            holding.getAsset(),
            PortfolioHoldingChangeType.ADJUSTED,
            holding.getQuantity(),
            holding.getAveragePurchasePrice(),
            LocalDate.now(SEOUL),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    return SavedPortfolioHoldingResponse.from(holding);
  }

  @Transactional
  public void remove(Long memberId, Long assetId) {
    Portfolio portfolio = getPortfolio(memberId);
    PortfolioHolding holding = getHolding(portfolio.getId(), assetId);
    historyRepository.save(
        new PortfolioHoldingHistory(
            portfolio,
            holding.getAsset(),
            PortfolioHoldingChangeType.REMOVED,
            holding.getQuantity(),
            holding.getAveragePurchasePrice(),
            LocalDate.now(SEOUL),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    holdingRepository.delete(holding);
  }

  @Transactional
  public PortfolioTransactionResponse transact(Long memberId, PortfolioTransactionRequest request) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    Portfolio portfolio = getOrCreatePortfolio(member);
    Asset asset =
        assetRepository
            .findById(request.assetId())
            .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
    PortfolioHolding current =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), request.assetId())
            .orElse(null);
    long currentQuantity = current == null ? 0 : current.getQuantity();
    BigDecimal currentAverage =
        current == null ? BigDecimal.ZERO.setScale(2) : current.getAveragePurchasePrice();
    PortfolioHoldingChangeType changeType =
        request.type() == PortfolioTransactionType.BUY
            ? PortfolioHoldingChangeType.BUY
            : PortfolioHoldingChangeType.SELL;
    PortfolioHoldingHistory history =
        historyRepository.saveAndFlush(
            new PortfolioHoldingHistory(
                portfolio,
                asset,
                changeType,
                currentQuantity,
                currentAverage,
                request.tradeDate(),
                request.quantity(),
                request.unitPrice(),
                request.fee(),
                request.tax()));

    PortfolioLedgerCalculator.LedgerResult ledger = replay(portfolio.getId());
    SavedPortfolioHoldingResponse responseHolding =
        applyLedgerPosition(portfolio, asset, current, ledger);
    snapshotTransactionService.refreshSnapshotsFrom(portfolio.getId(), request.tradeDate());
    return new PortfolioTransactionResponse(
        history.getId(), responseHolding, ledger.realizedProfitLoss());
  }

  @Transactional
  public PortfolioTransactionResponse updateTransaction(
      Long memberId, Long transactionId, PortfolioTransactionUpdateRequest request) {
    Portfolio portfolio = getPortfolio(memberId);
    PortfolioHoldingHistory history = getTransaction(portfolio.getId(), transactionId);
    LocalDate previousTradeDate = history.getEffectiveDate();
    history.updateTransaction(
        request.tradeDate(), request.quantity(), request.unitPrice(), request.fee(), request.tax());
    historyRepository.saveAndFlush(history);

    PortfolioLedgerCalculator.LedgerResult ledger = replay(portfolio.getId());
    Asset asset = history.getAsset();
    PortfolioHolding current =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
            .orElse(null);
    SavedPortfolioHoldingResponse responseHolding =
        applyLedgerPosition(portfolio, asset, current, ledger);
    LocalDate refreshFrom =
        previousTradeDate.isBefore(request.tradeDate()) ? previousTradeDate : request.tradeDate();
    snapshotTransactionService.refreshSnapshotsFrom(portfolio.getId(), refreshFrom);
    return new PortfolioTransactionResponse(
        history.getId(), responseHolding, ledger.realizedProfitLoss());
  }

  @Transactional
  public PortfolioTransactionResponse deleteTransaction(Long memberId, Long transactionId) {
    Portfolio portfolio = getPortfolio(memberId);
    PortfolioHoldingHistory history = getTransaction(portfolio.getId(), transactionId);
    Asset asset = history.getAsset();
    historyRepository.delete(history);
    historyRepository.flush();

    PortfolioLedgerCalculator.LedgerResult ledger = replay(portfolio.getId());
    PortfolioHolding current =
        holdingRepository
            .findByPortfolioIdAndAssetId(portfolio.getId(), asset.getId())
            .orElse(null);
    SavedPortfolioHoldingResponse responseHolding =
        applyLedgerPosition(portfolio, asset, current, ledger);
    snapshotTransactionService.refreshSnapshotsFrom(portfolio.getId(), history.getEffectiveDate());
    return new PortfolioTransactionResponse(
        transactionId, responseHolding, ledger.realizedProfitLoss());
  }

  @Transactional(readOnly = true)
  public List<PortfolioHoldingHistoryResponse> histories(
      Long memberId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_PERIOD);
    }
    Portfolio portfolio = getPortfolio(memberId);
    return historyRepository
        .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDesc(
            portfolio.getId(), from, to)
        .stream()
        .map(PortfolioHoldingHistoryResponse::from)
        .toList();
  }

  private Portfolio getOrCreatePortfolio(Member member) {
    return portfolioRepository
        .findByMemberId(member.getId())
        .orElseGet(() -> portfolioRepository.save(Portfolio.create(member)));
  }

  private Portfolio getPortfolio(Long memberId) {
    return portfolioRepository
        .findByMemberId(memberId)
        .orElseThrow(() -> new BusinessException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND));
  }

  private PortfolioHolding getHolding(Long portfolioId, Long assetId) {
    return holdingRepository
        .findByPortfolioIdAndAssetId(portfolioId, assetId)
        .orElseThrow(() -> new BusinessException(PortfolioErrorCode.PORTFOLIO_HOLDING_NOT_FOUND));
  }

  private PortfolioHoldingHistory getTransaction(Long portfolioId, Long transactionId) {
    return historyRepository
        .findByIdAndPortfolioId(transactionId, portfolioId)
        .filter(PortfolioHoldingHistory::isTransaction)
        .orElseThrow(
            () -> new BusinessException(PortfolioErrorCode.PORTFOLIO_TRANSACTION_NOT_FOUND));
  }

  private SavedPortfolioHoldingResponse applyLedgerPosition(
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
      PortfolioHolding saved =
          holdingRepository.save(
              new PortfolioHolding(
                  portfolio, asset, position.quantity(), position.averagePurchasePrice()));
      return SavedPortfolioHoldingResponse.from(saved);
    }
    current.change(position.quantity(), position.averagePurchasePrice());
    return SavedPortfolioHoldingResponse.from(current);
  }

  private PortfolioLedgerCalculator.LedgerResult replay(Long portfolioId) {
    return ledgerCalculator.replay(
        historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
            portfolioId));
  }
}
