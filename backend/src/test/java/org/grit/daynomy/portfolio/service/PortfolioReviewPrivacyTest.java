package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.junit.jupiter.api.Test;

class PortfolioReviewPrivacyTest {
  @Test
  void oldSnapshotsNeverExposePurchaseOrCurrentPricesInPublicResponses() throws Exception {
    var holding =
        new PortfolioAnalysisPublicSnapshot.HoldingContext(
            "005930",
            "KOSPI",
            null,
            new BigDecimal("210750"),
            new BigDecimal("208000"),
            "KRW",
            null,
            null,
            BigDecimal.ZERO);
    var impact =
        new PortfolioAnalysisPublicSnapshot.AssetImpact(
            "삼성전자", new BigDecimal("100"), null, null, "", "", "", "", "", List.of(), holding, 1);
    var snapshot =
        new PortfolioAnalysisPublicSnapshot(
            1,
            0,
            true,
            false,
            null,
            null,
            null,
            null,
            null,
            "",
            List.of(impact),
            List.of(),
            List.of());
    var json =
        new ObjectMapper()
            .writeValueAsString(PortfolioAnalysisShareDto.SnapshotResponse.from(snapshot));
    assertThat(json)
        .contains("assetCode", "returnRate")
        .doesNotContain("averagePurchasePrice", "currentPrice", "210750", "208000");
  }
}
