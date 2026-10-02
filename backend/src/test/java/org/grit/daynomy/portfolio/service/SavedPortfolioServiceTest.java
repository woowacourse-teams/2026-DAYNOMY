package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingCreateRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioHoldingUpdateRequest;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingRepository holdingRepository;
  @Mock private PortfolioHoldingHistoryRepository historyRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private AssetRepository assetRepository;
  @InjectMocks private SavedPortfolioService service;

  @Test
  void addHoldingSavesCurrentStateAndHistory() {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = asset();
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.existsByPortfolioIdAndAssetId(10L, 2L)).willReturn(false);
    given(assetRepository.findById(2L)).willReturn(Optional.of(asset));
    given(holdingRepository.save(any(PortfolioHolding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.add(1L, new SavedPortfolioHoldingCreateRequest(2L, 3L, new BigDecimal("10000")));

    assertThat(response.assetId()).isEqualTo(2L);
    assertThat(response.quantity()).isEqualTo(3L);
    ArgumentCaptor<PortfolioHoldingHistory> historyCaptor =
        ArgumentCaptor.forClass(PortfolioHoldingHistory.class);
    then(historyRepository).should().save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getChangeType())
        .isEqualTo(PortfolioHoldingChangeType.ADDED);
  }

  @Test
  void updateHoldingRecordsUpdatedHistory() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = asset();
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(5L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("11000"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));

    service.update(1L, 2L, new SavedPortfolioHoldingUpdateRequest(5L, new BigDecimal("11000")));

    ArgumentCaptor<PortfolioHoldingHistory> historyCaptor =
        ArgumentCaptor.forClass(PortfolioHoldingHistory.class);
    then(historyRepository).should().save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getChangeType())
        .isEqualTo(PortfolioHoldingChangeType.UPDATED);
  }

  @Test
  void removeHoldingRecordsRemovedHistory() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = mock(Asset.class);
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(3L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("10000"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findByPortfolioIdAndAssetId(10L, 2L)).willReturn(Optional.of(holding));

    service.remove(1L, 2L);

    ArgumentCaptor<PortfolioHoldingHistory> historyCaptor =
        ArgumentCaptor.forClass(PortfolioHoldingHistory.class);
    then(historyRepository).should().save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getChangeType())
        .isEqualTo(PortfolioHoldingChangeType.REMOVED);
    then(holdingRepository).should().delete(holding);
  }

  @Test
  void historiesUsesExclusiveNextDayBoundary() {
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    LocalDate date = LocalDate.of(2026, 9, 30);

    service.histories(1L, date, date);

    then(historyRepository)
        .should()
        .findAllByPortfolioIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            10L,
            date.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant(),
            date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant());
  }

  private Asset asset() {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(2L);
    given(asset.getAssetCode()).willReturn("005930");
    given(asset.getName()).willReturn("삼성전자");
    given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    given(asset.getMarket()).willReturn(StockMarket.KOSPI);
    return asset;
  }
}
