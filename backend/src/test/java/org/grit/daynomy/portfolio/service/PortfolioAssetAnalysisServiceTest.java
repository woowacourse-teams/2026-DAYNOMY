package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.AssetImpactResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.domain.PortfolioAssetAnalysis;
import org.grit.daynomy.portfolio.repository.PortfolioAssetAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioAssetAnalysisServiceTest {
  @Mock private PortfolioAssetAnalysisRepository repository;
  @Mock private PortfolioAnalysisAiClient ai;
  private PortfolioAssetAnalysisService service;
  private final List<PortfolioAnalysisTarget> targets =
      List.of(new PortfolioAnalysisTarget("삼성전자"), new PortfolioAnalysisTarget("SK하이닉스"));

  @BeforeEach
  void setUp() {
    service =
        new PortfolioAssetAnalysisService(repository, ai, "test-model", Duration.ofMinutes(30));
  }

  @Test
  void analyzesAllMissingAssetsInOneRequestAndStoresOnlyCommonData() throws Exception {
    given(ai.analyze(targets))
        .willReturn(result(impact("삼성전자", ImpactLevel.LOW), impact("SK하이닉스", ImpactLevel.HIGH)));

    var response = service.analyze(targets);

    verify(ai).analyze(targets);
    assertThat(response.impacts())
        .extracting(AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(response.impacts()).extracting(AssetImpactResult::sortOrder).containsExactly(1, 2);
    var snapshot = ArgumentCaptor.forClass(String.class);
    var analyzedAt = ArgumentCaptor.forClass(Instant.class);
    var expiresAt = ArgumentCaptor.forClass(Instant.class);
    verify(repository, times(2))
        .upsert(
            anyString(),
            eq("asset-v1"),
            eq("test-model"),
            snapshot.capture(),
            analyzedAt.capture(),
            expiresAt.capture());
    for (int i = 0; i < 2; i++) {
      var json = new ObjectMapper().readTree(snapshot.getAllValues().get(i));
      assertThat(json.toString()).doesNotContain("weight", "quantity", "memberId", "overallImpact");
      assertThat(json.path("sortOrder").asInt()).isZero();
      assertThat(json.path("sources").get(0).path("factQuote").asText())
          .isEqualTo("Revenue increased.");
      assertThat(
              Duration.between(analyzedAt.getAllValues().get(i), expiresAt.getAllValues().get(i)))
          .isEqualTo(Duration.ofMinutes(30));
    }
  }

  @Test
  void reusesAllValidResultsWithoutAiOrExtendingExpiry() {
    var savedResults =
        List.of(saved(impact("삼성전자", ImpactLevel.LOW)), saved(impact("SK하이닉스", ImpactLevel.HIGH)));
    given(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                eq(List.of("삼성전자", "SK하이닉스")), eq("asset-v1"), eq("test-model"), any()))
        .willReturn(savedResults);

    var response = service.analyze(targets);

    verifyNoInteractions(ai);
    verify(repository, never())
        .upsert(anyString(), anyString(), anyString(), anyString(), any(), any());
    assertThat(response.impacts())
        .extracting(AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(response.impacts()).extracting(AssetImpactResult::sortOrder).containsExactly(1, 2);
    assertThat(response.sources()).hasSize(1);
    assertThat(response.overallImpact()).isEmpty();
  }

  @Test
  void analyzesOnlyMissingAssetsAndMergesWithSavedResults() {
    var savedResult = saved(impact("삼성전자", ImpactLevel.HIGH));
    given(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                anyList(), anyString(), anyString(), any()))
        .willReturn(List.of(savedResult));
    given(ai.analyze(List.of(targets.get(1))))
        .willReturn(result(impact("SK하이닉스", ImpactLevel.LOW)));

    var response = service.analyze(targets);

    verify(ai).analyze(List.of(targets.get(1)));
    verifyNoMoreInteractions(ai);
    verify(repository, never())
        .upsert(eq("삼성전자"), anyString(), anyString(), anyString(), any(), any());
    assertThat(response.impacts())
        .extracting(AssetImpactResult::assetName)
        .containsExactly("삼성전자", "SK하이닉스");
  }

  @Test
  void doesNotStoreInsufficientEvidenceButPreservesPartialResponse() {
    var insufficient =
        new AssetImpactResult(
            "삼성전자", null, null, "근거 부족이에요.", "", "", "근거 부족이에요.", "", List.of(), 1);
    given(ai.analyze(targets)).willReturn(result(insufficient, impact("SK하이닉스", ImpactLevel.HIGH)));

    var response = service.analyze(targets);

    verify(repository, never())
        .upsert(eq("삼성전자"), anyString(), anyString(), anyString(), any(), any());
    verify(repository).upsert(eq("SK하이닉스"), anyString(), anyString(), anyString(), any(), any());
    assertThat(response.impacts()).hasSize(2);
    assertThat(response.impacts().getLast().direction()).isNull();
  }

  @Test
  void failedAiDoesNotStoreResults() {
    given(ai.analyze(targets)).willThrow(new IllegalStateException("외부 호출 실패"));
    assertThatThrownBy(() -> service.analyze(targets)).isInstanceOf(IllegalStateException.class);
    verify(repository, never())
        .upsert(anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void emptyTargetsDoNotCallDatabaseOrAi() {
    assertThat(service.analyze(List.of()).impacts()).isEmpty();
    verifyNoInteractions(repository, ai);
  }

  private PortfolioAssetAnalysis saved(AssetImpactResult impact) {
    var saved = mock(PortfolioAssetAnalysis.class);
    given(saved.getAssetName()).willReturn(impact.assetName());
    given(saved.getSnapshot()).willReturn(impact);
    return saved;
  }

  private PortfolioAnalysisResult result(AssetImpactResult... impacts) {
    return new PortfolioAnalysisResult("", List.of(impacts), List.of());
  }

  private AssetImpactResult impact(String name, ImpactLevel level) {
    var source =
        new PortfolioAnalysisResult.Source(
            "실적 발표",
            "https://example.com/earnings",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 1),
            "매출이 늘었어요.",
            "Revenue increased.");
    return new AssetImpactResult(
        name,
        ImpactDirection.POSITIVE,
        level,
        "매출이 늘었어요.",
        "실적에 유리해요.",
        "다음 실적을 확인해요.",
        "매출 증가를 반영했어요.",
        source.excerpt(),
        List.of(source),
        9);
  }
}
