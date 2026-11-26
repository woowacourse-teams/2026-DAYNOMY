package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisShareSnapshotFactoryTest {

  private final PortfolioAnalysisShareSnapshotFactory factory =
      new PortfolioAnalysisShareSnapshotFactory(mock(PortfolioAnalysisEvidenceRepository.class));

  @Test
  void createsPortfolioOnlySnapshotFromSelectedPricedAssets() {
    PortfolioHoldingResponse samsung = holding("삼성전자", "60");
    PortfolioHoldingResponse treasury = holding("미국채 ETF", "40");

    var snapshot =
        factory.fromPortfolio(List.of(samsung, treasury), Map.of(), List.of("삼성전자", "미국채 ETF"));

    assertThat(snapshot.allAssetsShared()).isTrue();
    assertThat(snapshot.hasAnalysis()).isFalse();
    assertThat(snapshot.impacts())
        .extracting(impact -> impact.assetName())
        .containsExactly("삼성전자", "미국채 ETF");
    assertThat(snapshot.impacts())
        .extracting(impact -> impact.weight())
        .containsExactly(new BigDecimal("60"), new BigDecimal("40"));
  }

  @Test
  void rejectsDuplicateOrUnavailableRequestedAssets() {
    PortfolioHoldingResponse samsung = holding("삼성전자", "100");

    assertThatThrownBy(
            () -> factory.fromPortfolio(List.of(samsung), Map.of(), List.of("삼성전자", "삼성전자")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS));
    assertThatThrownBy(() -> factory.fromPortfolio(List.of(samsung), Map.of(), List.of("없는 종목")))
        .isInstanceOf(BusinessException.class);
  }

  private PortfolioHoldingResponse holding(String name, String weight) {
    PortfolioHoldingResponse holding = mock(PortfolioHoldingResponse.class);
    when(holding.name()).thenReturn(name);
    when(holding.priceAvailable()).thenReturn(true);
    when(holding.weight()).thenReturn(new BigDecimal(weight));
    return holding;
  }
}
