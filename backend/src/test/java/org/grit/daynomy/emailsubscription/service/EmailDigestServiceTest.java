package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.*;
import org.grit.daynomy.content.domain.*;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.repository.*;
import org.grit.daynomy.portfolio.domain.*;
import org.grit.daynomy.portfolio.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDigestServiceTest {
  final ImportantStockSelectionService selection = mock(ImportantStockSelectionService.class);
  final PortfolioRepository portfolios = mock(PortfolioRepository.class);
  final PortfolioHoldingRepository holdings = mock(PortfolioHoldingRepository.class);
  final PortfolioHoldingHistoryRepository histories = mock(PortfolioHoldingHistoryRepository.class);
  final PortfolioDailySnapshotRepository snapshots = mock(PortfolioDailySnapshotRepository.class);
  final MarketTradingDayRepository days = mock(MarketTradingDayRepository.class);
  final EmailStockContentRepository contents = mock(EmailStockContentRepository.class);
  final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
  final EmailDigestService service =
      new EmailDigestService(
          selection, portfolios, holdings, histories, snapshots, days, contents, deliveries);
  final Portfolio portfolio = mock(Portfolio.class);
  final Asset asset = Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, null, MAIL_DATE);

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(asset, "id", 1L);
    when(portfolio.getId()).thenReturn(10L);
    when(portfolios.findByMemberId(1L)).thenReturn(Optional.of(portfolio));
    when(holdings.findAllByPortfolioIdOrderById(10L))
        .thenReturn(List.of(new PortfolioHolding(portfolio, asset, 1, BigDecimal.TEN)));
  }

  private AssetContent item(long id, String url) {
    var c =
        AssetContent.create(
            asset, ContentSourceType.THREADS, "새 관련 글", url, null, MAIL_DATE.minusYears(2));
    ReflectionTestUtils.setField(c, "id", id);
    ReflectionTestUtils.setField(c, "createdAt", NOW.minusSeconds(60));
    return c;
  }

  @Test
  void contentCanBeSentWithoutPriceDataOrAnyAnalysis() {
    var item = item(1L, "https://threads.net/post/1");
    when(contents.findRelatedCandidates(anyList(), anyList(), any(), any()))
        .thenReturn(List.of(item));
    var result = service.compose(1L, NOW).orElseThrow();
    assertThat(result.stockDigest().stocks()).isEmpty();
    assertThat(result.stockDigest().contents()).hasSize(1);
    assertThat(result.stockDigest().emailSections("https://daynomy.com"))
        .contains("데이노미 등록일 2026-10-26")
        .doesNotContain("2024-10-26", "분석이 달라");
  }

  @Test
  void deduplicatesMaterialUrlsAndPreviouslyCoveredItemsAndCapsAtThree() {
    when(contents.findRelatedCandidates(anyList(), anyList(), any(), any()))
        .thenReturn(
            List.of(
                item(1L, "https://threads.net/1"),
                item(2L, "https://threads.net/1"),
                item(3L, "https://threads.net/2"),
                item(4L, "https://threads.net/3"),
                item(5L, "https://threads.net/4"),
                item(6L, "https://threads.net/5")));
    when(deliveries.findCoveredContentKeys(anyLong(), anyList()))
        .thenReturn(List.of("THREADS|https://threads.net/1"));
    var result = service.compose(1L, NOW).orElseThrow();
    assertThat(result.stockDigest().contents())
        .extracting(EmailStockDigest.Content::id)
        .containsExactly(3L, 4L, 5L);
  }

  @Test
  void doesNotSendWhenAllChangesAreAlreadyCoveredOrThereIsNoData() {
    assertThat(service.compose(1L, NOW)).isEmpty();
    when(selection.select(1L, MAIL_DATE))
        .thenReturn(
            Optional.of(
                new ImportantStockSelection(
                    MAIL_DATE.minusDays(3),
                    MAIL_DATE.minusDays(4),
                    true,
                    List.of(
                        new ImportantStockSelection.Stock(
                            1L, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE)))));
    when(deliveries.findCoveredPriceAssetIds(anyLong(), anyString(), anyList()))
        .thenReturn(List.of(1L));
    assertThat(service.compose(1L, NOW)).isEmpty();
  }

  @Test
  void excludesUnsafeUnheldAndUnpublishedMaterials() {
    when(contents.findRelatedCandidates(anyList(), anyList(), any(), any()))
        .thenReturn(List.of(item(1L, "javascript:alert(1)")));
    assertThat(service.compose(1L, NOW)).isEmpty();
  }

  @Test
  void rechecksDeletedMaterialsAndChangedHoldingsBeforeSending() {
    var item = item(1L, "https://threads.net/post/1");
    when(contents.findRelatedCandidates(anyList(), anyList(), any(), any()))
        .thenReturn(List.of(item));
    var prepared = service.compose(1L, NOW).orElseThrow();
    when(contents.findCurrentByIds(List.of(1L))).thenReturn(List.of(item));
    assertThat(service.stillValid(1L, NOW, prepared.stockDigest(), prepared.supplement())).isTrue();
    when(contents.findCurrentByIds(List.of(1L))).thenReturn(List.of());
    assertThat(service.stillValid(1L, NOW, prepared.stockDigest(), prepared.supplement()))
        .isFalse();
    when(holdings.findAllByPortfolioIdOrderById(10L))
        .thenReturn(List.of(new PortfolioHolding(portfolio, asset, 2, BigDecimal.TEN)));
    assertThat(service.stillValid(1L, NOW, prepared.stockDigest(), prepared.supplement()))
        .isFalse();
  }

  @Test
  void sendsAssetsOnlyAtTwoPercentAndRejectsSnapshotsAfterHoldingChanges() {
    var base = MAIL_DATE.minusDays(3);
    when(days
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                MAIL_DATE.minusDays(1)))
        .thenReturn(Optional.of(new MarketTradingDay(base, true)));
    var snapshot = new PortfolioDailySnapshot(portfolio, base);
    snapshot.update(
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(102),
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(2));
    when(snapshots.findByPortfolioIdAndBaseDate(10L, base)).thenReturn(Optional.of(snapshot));
    assertThat(service.compose(1L, NOW)).isPresent();
    when(histories
            .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
                10L, base.plusDays(1), MAIL_DATE))
        .thenReturn(List.of(mock(PortfolioHoldingHistory.class)));
    assertThat(service.compose(1L, NOW)).isEmpty();
  }
}
