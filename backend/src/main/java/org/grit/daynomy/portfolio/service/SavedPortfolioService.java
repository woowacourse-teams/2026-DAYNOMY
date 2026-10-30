package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
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
import org.grit.daynomy.portfolio.domain.PortfolioTransactionImport;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingHistoryResponse;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingsCreateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingsUpdateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionImportRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionImportResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingUpdateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.exception.PortfolioImportRowError;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.repository.PortfolioTransactionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
  private final PortfolioExchangeRateService exchangeRates;
  private final PlatformTransactionManager transactionManager;
  private final PortfolioTransactionImportRepository importRepository;

  @Transactional
  public SavedPortfolioResponse createHoldings(
      Long memberId, PortfolioHoldingsCreateRequest request) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    Portfolio portfolio = getOrCreatePortfolio(member);
    var assetIds = new HashSet<Long>();
    holdingRepository
        .findAllByPortfolioIdOrderById(portfolio.getId())
        .forEach(holding -> assetIds.add(holding.getAsset().getId()));
    var added =
        request.holdings().stream()
            .map(
                item -> {
                  if (!assetIds.add(item.assetId()))
                    throw new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
                  Asset asset =
                      assetRepository
                          .findById(item.assetId())
                          .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
                  return new PortfolioHolding(
                      portfolio,
                      asset,
                      item.quantity(),
                      item.averagePurchasePrice(),
                      item.nativeAveragePurchasePrice());
                })
            .toList();
    holdingRepository.saveAll(added);
    historyRepository.saveAll(
        added.stream()
            .map(
                holding ->
                    new PortfolioHoldingHistory(
                        holding, PortfolioHoldingChangeType.OPENING_BALANCE))
            .toList());
    return get(memberId);
  }

  @Transactional
  public List<SavedPortfolioHoldingResponse> updateHoldings(
      Long memberId, PortfolioHoldingsUpdateRequest request) {
    Portfolio portfolio = getPortfolioForUpdate(memberId);
    if (request.holdings().stream().map(item -> item.assetId()).distinct().count()
        != request.holdings().size()) {
      throw new BusinessException(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    }
    // Resolve every holding before mutating any of them. The portfolio lock covers the whole batch.
    var holdings =
        request.holdings().stream()
            .map(item -> getHolding(portfolio.getId(), item.assetId()))
            .toList();
    for (int index = 0; index < holdings.size(); index++) {
      var holding = holdings.get(index);
      var item = request.holdings().get(index);
      if (holding.change(
          item.quantity(), item.averagePurchasePrice(), item.nativeAveragePurchasePrice())) {
        historyRepository.save(
            new PortfolioHoldingHistory(holding, PortfolioHoldingChangeType.ADJUSTED));
      }
    }
    return holdings.stream().map(SavedPortfolioHoldingResponse::from).toList();
  }

  public PortfolioTransactionImportResponse importTransactions(
      Long memberId, PortfolioTransactionImportRequest request) {
    return createTransactions(memberId, request, true);
  }

  public PortfolioTransactionImportResponse createTransactions(
      Long memberId, PortfolioTransactionImportRequest request) {
    return createTransactions(memberId, request, false);
  }

  private PortfolioTransactionImportResponse createTransactions(
      Long memberId, PortfolioTransactionImportRequest request, boolean deduplicate) {
    var ordered =
        request.transactions().stream()
            .sorted(java.util.Comparator.comparing(PortfolioTransactionRequest::tradeDate))
            .toList();
    String fingerprint =
        PortfolioTransactionImport.fingerprint(
            ordered.stream()
                .map(
                    item ->
                        new PortfolioTransactionImport.Entry(
                            item.assetId(),
                            item.type(),
                            item.tradeDate(),
                            item.quantity(),
                            item.unitPrice(),
                            item.fee(),
                            item.tax(),
                            item.exchangeRate()))
                .toList());
    var rates =
        exchangeRates.forImport(
            memberId,
            ordered.stream().map(PortfolioTransactionRequest::assetId).distinct().toList(),
            ordered.getFirst().tradeDate());
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              Member member =
                  memberRepository
                      .findByIdForUpdate(memberId)
                      .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
              Portfolio portfolio = getOrCreatePortfolio(member);
              if (deduplicate
                  && importRepository.existsByPortfolioIdAndFingerprint(
                      portfolio.getId(), fingerprint)) {
                return new PortfolioTransactionImportResponse(0, true, get(memberId));
              }
              if (!deduplicate) {
                var assetIds = new HashSet<Long>();
                holdingRepository
                    .findAllByPortfolioIdOrderById(portfolio.getId())
                    .forEach(holding -> assetIds.add(holding.getAsset().getId()));
                for (int index = 0; index < request.transactions().size(); index++) {
                  if (!assetIds.add(request.transactions().get(index).assetId())) {
                    throw new BusinessException(
                        new PortfolioImportRowError(
                            PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET, index + 1));
                  }
                }
              }
              for (int index = 0; index < ordered.size(); index++) {
                try {
                  transactInTransaction(memberId, ordered.get(index), rates, false);
                } catch (BusinessException exception) {
                  // Include only the source row number, never the uploaded financial values.
                  int row = request.transactions().indexOf(ordered.get(index)) + 1;
                  throw new BusinessException(
                      new PortfolioImportRowError(exception.errorCode(), row));
                }
              }
              snapshotTransactionService.refreshSnapshotsFrom(
                  portfolio.getId(), ordered.getFirst().tradeDate(), rates);
              if (deduplicate)
                importRepository.save(new PortfolioTransactionImport(portfolio, fingerprint));
              return new PortfolioTransactionImportResponse(ordered.size(), false, get(memberId));
            });
  }

  @Transactional(readOnly = true)
  public SavedPortfolioResponse get(Long memberId) {
    return portfolioRepository
        .findByMemberId(memberId)
        .map(
            portfolio -> {
              PortfolioLedgerCalculator.LedgerResult ledger = replay(portfolio.getId());
              return new SavedPortfolioResponse(
                  holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()).stream()
                      .map(
                          holding ->
                              SavedPortfolioHoldingResponse.from(
                                  holding,
                                  ledger.positions().containsKey(holding.getAsset().getId())
                                      ? ledger
                                          .positions()
                                          .get(holding.getAsset().getId())
                                          .nativeAveragePurchasePrice()
                                      : holding.getNativeAveragePurchasePrice()))
                      .toList(),
                  ledger.realizedProfitLoss());
            })
        .orElseGet(SavedPortfolioResponse::empty);
  }

  @Transactional
  public SavedPortfolioHoldingResponse update(
      Long memberId, Long assetId, SavedPortfolioHoldingUpdateRequest request) {
    Portfolio portfolio = getPortfolioForUpdate(memberId);
    PortfolioHolding holding = getHolding(portfolio.getId(), assetId);
    if (holding.change(request.quantity(), request.averagePurchasePrice())) {
      historyRepository.save(
          new PortfolioHoldingHistory(holding, PortfolioHoldingChangeType.ADJUSTED));
    }
    return SavedPortfolioHoldingResponse.from(holding);
  }

  @Transactional
  public void remove(Long memberId, Long assetId) {
    Portfolio portfolio = getPortfolioForUpdate(memberId);
    PortfolioHolding holding = getHolding(portfolio.getId(), assetId);
    historyRepository.save(
        new PortfolioHoldingHistory(holding, PortfolioHoldingChangeType.REMOVED));
    holdingRepository.delete(holding);
  }

  public PortfolioTransactionResponse transact(Long memberId, PortfolioTransactionRequest request) {
    var rates = exchangeRates.forMember(memberId, request.assetId(), request.tradeDate());
    return new TransactionTemplate(transactionManager)
        .execute(status -> transactInTransaction(memberId, request, rates, true));
  }

  private PortfolioTransactionResponse transactInTransaction(
      Long memberId,
      PortfolioTransactionRequest request,
      Map<LocalDate, BigDecimal> rates,
      boolean refreshSnapshots) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    Portfolio portfolio = getOrCreatePortfolio(member);
    Asset asset =
        assetRepository
            .findById(request.assetId())
            .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
    PortfolioHoldingHistory.validateExchangeRate(asset, request.exchangeRate());
    validateTradeDate(portfolio.getId(), asset.getId(), request.tradeDate());
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
            request.tax());
    history.applyExchangeRate(request.exchangeRate());
    history.recordTradeTime(request.tradeTime());
    historyRepository.saveAndFlush(history);

    PortfolioLedgerCalculator.LedgerResult ledger = replay(portfolio.getId());
    SavedPortfolioHoldingResponse responseHolding =
        applyLedgerPosition(portfolio, asset, current, ledger);
    if (refreshSnapshots) {
      snapshotTransactionService.refreshSnapshotsFrom(
          portfolio.getId(), request.tradeDate(), rates);
    }
    return new PortfolioTransactionResponse(
        history.getId(), responseHolding, ledger.realizedProfitLoss());
  }

  public PortfolioTransactionResponse updateTransaction(
      Long memberId, Long transactionId, PortfolioTransactionUpdateRequest request) {
    var rates = exchangeRates.forTransaction(memberId, transactionId, request.tradeDate());
    return new TransactionTemplate(transactionManager)
        .execute(status -> updateTransactionInTransaction(memberId, transactionId, request, rates));
  }

  private PortfolioTransactionResponse updateTransactionInTransaction(
      Long memberId,
      Long transactionId,
      PortfolioTransactionUpdateRequest request,
      Map<LocalDate, BigDecimal> rates) {
    Portfolio portfolio = getPortfolioForUpdate(memberId);
    PortfolioHoldingHistory history = getTransaction(portfolio.getId(), transactionId);
    validateTradeDate(portfolio.getId(), history.getAsset().getId(), request.tradeDate());
    LocalDate previousTradeDate = history.getEffectiveDate();
    history.applyExchangeRate(request.exchangeRate());
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
    snapshotTransactionService.refreshSnapshotsFrom(portfolio.getId(), refreshFrom, rates);
    return new PortfolioTransactionResponse(
        history.getId(), responseHolding, ledger.realizedProfitLoss());
  }

  public PortfolioTransactionResponse deleteTransaction(Long memberId, Long transactionId) {
    var rates = exchangeRates.forTransaction(memberId, transactionId, null);
    return new TransactionTemplate(transactionManager)
        .execute(status -> deleteTransactionInTransaction(memberId, transactionId, rates));
  }

  private PortfolioTransactionResponse deleteTransactionInTransaction(
      Long memberId, Long transactionId, Map<LocalDate, BigDecimal> rates) {
    Portfolio portfolio = getPortfolioForUpdate(memberId);
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
    snapshotTransactionService.refreshSnapshotsFrom(
        portfolio.getId(), history.getEffectiveDate(), rates);
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
        .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
            portfolio.getId(), from, to)
        .stream()
        .map(PortfolioHoldingHistoryResponse::from)
        .toList();
  }

  private Portfolio getOrCreatePortfolio(Member member) {
    return portfolioRepository
        .findByMemberId(member.getId())
        .map(this::lockPortfolio)
        .orElseGet(() -> portfolioRepository.save(Portfolio.create(member)));
  }

  private Portfolio getPortfolioForUpdate(Long memberId) {
    return lockPortfolio(getPortfolio(memberId));
  }

  private Portfolio lockPortfolio(Portfolio portfolio) {
    // Lock before loading ledger or holdings so concurrent mutations replay committed history.
    return portfolioRepository
        .findByIdForUpdate(portfolio.getId())
        .orElseThrow(() -> new BusinessException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND));
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
                  portfolio,
                  asset,
                  position.quantity(),
                  position.averagePurchasePrice(),
                  position.nativeAveragePurchasePrice()));
      return SavedPortfolioHoldingResponse.from(saved);
    }
    current.change(
        position.quantity(),
        position.averagePurchasePrice(),
        position.nativeAveragePurchasePrice());
    return SavedPortfolioHoldingResponse.from(current);
  }

  private void validateTradeDate(Long portfolioId, Long assetId, LocalDate tradeDate) {
    boolean hasLaterBalance =
        historyRepository
            .findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(portfolioId)
            .stream()
            .filter(history -> history.getAsset().getId().equals(assetId))
            .anyMatch(history -> history.isBalanceAfter(tradeDate));
    if (hasLaterBalance) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_TRADE_DATE);
    }
  }

  private PortfolioLedgerCalculator.LedgerResult replay(Long portfolioId) {
    return ledgerCalculator.replay(
        historyRepository.findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
            portfolioId));
  }
}
