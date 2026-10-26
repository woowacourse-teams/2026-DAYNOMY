package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification;
import org.grit.daynomy.emailsubscription.repository.CommonStockAnalysisRepository;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommonStockAnalysisServiceTest {

  @Mock private EmailDeliveryRepository deliveries;
  @Mock private AssetRepository assets;
  @Mock private CommonStockAnalysisRepository analyses;
  @InjectMocks private CommonStockAnalysisService service;

  @Test
  void invalidatesAndCancelsQueuedMailInTheSameOperation() {
    var analysis = mock(CommonStockAnalysis.class);
    when(analyses.findByIdForUpdate(1L)).thenReturn(Optional.of(analysis));
    when(deliveries.cancelQueuedForAnalysis(1L)).thenReturn(3);
    var at = Instant.parse("2026-10-26T00:00:00Z");
    when(analysis.getInvalidatedAt()).thenReturn(at);
    var result = service.invalidate(1L, "원문 정정");
    assertThat(result.cancelledDeliveries()).isEqualTo(3);
    verify(analysis).invalidate(eq("원문 정정"), any());
  }

  @Test
  void cannotStoreAnAnalysisForAnUnknownAsset() {
    when(assets.findById(1L)).thenReturn(Optional.empty());
    var key = new CommonStockAnalysisKey(1L, "model", "v1", "e1", Instant.EPOCH);
    var content = new CommonStockAnalysisContent(null, null, "", "", "", "", List.of());

    assertThatThrownBy(() -> service.storePending(key, content))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(analyses);
  }

  @Test
  void cannotApplyAFutureVerification() {
    var report =
        new CommonStockAnalysisVerification(
            "a".repeat(64), Instant.now().plusSeconds(3600), List.of());

    assertThatThrownBy(() -> service.recordVerification(1L, report))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(assets, analyses);
  }

  @Test
  void cannotRecordVerificationWithoutItsAnalysis() {
    when(analyses.findByIdForUpdate(1L)).thenReturn(Optional.empty());
    var report = new CommonStockAnalysisVerification("a".repeat(64), Instant.EPOCH, List.of());

    assertThatThrownBy(() -> service.recordVerification(1L, report))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(assets);
  }
}
