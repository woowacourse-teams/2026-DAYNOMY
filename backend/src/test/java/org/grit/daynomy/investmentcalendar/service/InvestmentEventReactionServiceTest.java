package org.grit.daynomy.investmentcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.investmentcalendar.domain.AssetEventReaction;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentEventReactionServiceTest {

  @Mock StockDailyPriceRepository priceRepository;

  @Test
  void excludesAnAssetWithoutEnoughPrices() {
    Asset firstAsset = asset(1L, "000001");
    Asset secondAsset = asset(2L, "000002");
    InvestmentEvent event = event();
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                List.of(1L, 2L), LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-23")))
        .willReturn(
            List.of(
                price(firstAsset, "2026-10-12", "10000"),
                price(firstAsset, "2026-10-13", "10100"),
                price(secondAsset, "2026-10-13", "20000")));
    InvestmentEventReactionService service = new InvestmentEventReactionService(priceRepository);

    List<AssetEventReaction> result = service.calculate(event, List.of(1L, 2L));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().assetId()).isEqualTo(1L);
  }

  private InvestmentEvent event() {
    return new InvestmentEvent(
        InvestmentEventType.US_CPI,
        "미국 소비자물가지수 발표",
        Instant.parse("2026-10-13T00:30:00Z"),
        new BigDecimal("2.90"),
        new BigDecimal("3.00"),
        "%",
        "미국 노동통계국",
        "https://www.bls.gov/cpi/",
        "BLS-CPI-2026-10",
        null);
  }

  private Asset asset(Long id, String code) {
    Asset asset = new Asset("테스트 종목", AssetCategory.STOCK, code);
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }

  private StockDailyPrice price(Asset asset, String date, String closePrice) {
    return new StockDailyPrice(asset, LocalDate.parse(date), new BigDecimal(closePrice));
  }
}
