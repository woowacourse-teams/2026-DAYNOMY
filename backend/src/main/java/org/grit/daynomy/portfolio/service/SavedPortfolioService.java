package org.grit.daynomy.portfolio.service;

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
import org.grit.daynomy.portfolio.dto.PortfolioHoldingHistoryResponse;
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

  @Transactional(readOnly = true)
  public SavedPortfolioResponse get(Long memberId) {
    return portfolioRepository
        .findByMemberId(memberId)
        .map(
            portfolio ->
                new SavedPortfolioResponse(
                    holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()).stream()
                        .map(SavedPortfolioHoldingResponse::from)
                        .toList()))
        .orElseGet(SavedPortfolioResponse::empty);
  }

  @Transactional
  public SavedPortfolioHoldingResponse add(
      Long memberId, SavedPortfolioHoldingCreateRequest request) {
    Portfolio portfolio = getOrCreatePortfolio(memberId);
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
            PortfolioHoldingChangeType.ADDED,
            holding.getQuantity(),
            holding.getAveragePurchasePrice()));
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
            PortfolioHoldingChangeType.UPDATED,
            holding.getQuantity(),
            holding.getAveragePurchasePrice()));
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
            holding.getAveragePurchasePrice()));
    holdingRepository.delete(holding);
  }

  @Transactional(readOnly = true)
  public List<PortfolioHoldingHistoryResponse> histories(
      Long memberId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new BusinessException(PortfolioErrorCode.INVALID_PORTFOLIO_PERIOD);
    }
    Portfolio portfolio = getPortfolio(memberId);
    return historyRepository
        .findAllByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            portfolio.getId(),
            from.atStartOfDay(SEOUL).toInstant(),
            to.plusDays(1).atStartOfDay(SEOUL).toInstant())
        .stream()
        .map(PortfolioHoldingHistoryResponse::from)
        .toList();
  }

  private Portfolio getOrCreatePortfolio(Long memberId) {
    return portfolioRepository
        .findByMemberId(memberId)
        .orElseGet(
            () -> {
              Member member =
                  memberRepository
                      .findById(memberId)
                      .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
              return portfolioRepository.save(Portfolio.create(member));
            });
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
}
