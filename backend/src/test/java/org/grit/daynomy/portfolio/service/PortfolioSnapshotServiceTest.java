package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotServiceTest {

  @Mock private PortfolioRepository portfolioRepository;
  @Mock private PortfolioHoldingRepository holdingRepository;
  @Mock private PortfolioDailySnapshotRepository snapshotRepository;
  @Mock private StockDailyPriceRepository stockPriceRepository;
  @InjectMocks private PortfolioSnapshotService service;

  @Test
  void createLatestSnapshotsCalculatesBookCostReturn() {
    LocalDate baseDate = LocalDate.of(2026, 9, 30);
    Portfolio portfolio = mock(Portfolio.class);
    given(portfolio.getId()).willReturn(10L);
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(2L);
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    given(holding.getQuantity()).willReturn(2L);
    given(holding.getAveragePurchasePrice()).willReturn(new BigDecimal("10000"));
    StockDailyPrice price = mock(StockDailyPrice.class);
    given(price.getBaseDate()).willReturn(baseDate);
    given(price.getClosePrice()).willReturn(new BigDecimal("12000"));
    given(stockPriceRepository.findFirstByOrderByBaseDateDesc()).willReturn(Optional.of(price));
    given(portfolioRepository.findAll()).willReturn(List.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(List.of(holding));
    given(stockPriceRepository.findByAssetIdAndBaseDate(2L, baseDate))
        .willReturn(Optional.of(price));
    given(snapshotRepository.findByPortfolioIdAndBaseDate(10L, baseDate))
        .willReturn(Optional.empty());

    int count = service.createLatestSnapshots();

    assertThat(count).isEqualTo(1);
    ArgumentCaptor<PortfolioDailySnapshot> snapshotCaptor =
        ArgumentCaptor.forClass(PortfolioDailySnapshot.class);
    then(snapshotRepository).should().save(snapshotCaptor.capture());
    assertThat(snapshotCaptor.getValue().getTotalPurchaseAmount()).isEqualByComparingTo("20000.00");
    assertThat(snapshotCaptor.getValue().getTotalEvaluationAmount())
        .isEqualByComparingTo("24000.00");
    assertThat(snapshotCaptor.getValue().getTotalReturnRate()).isEqualByComparingTo("20.00");
  }
}
