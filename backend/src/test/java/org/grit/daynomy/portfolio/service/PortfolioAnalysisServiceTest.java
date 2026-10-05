package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisSourceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisServiceTest {

  @Mock private PortfolioAnalysisAiClient portfolioAnalysisAiClient;

  @InjectMocks private PortfolioAnalysisService portfolioAnalysisService;

  @Test
  @DisplayName("요청한 포트폴리오 자산을 AI로 분석하고 응답 DTO로 변환한다")
  void analyzeReturnsPortfolioAnalysis() {
    PortfolioAnalysisRequest request = request(asset(" 삼성전자 ", "78"), asset("SK하이닉스", "22"));
    List<PortfolioAnalysisTarget> targets =
        List.of(
            new PortfolioAnalysisTarget("삼성전자", new BigDecimal("78")),
            new PortfolioAnalysisTarget("SK하이닉스", new BigDecimal("22")));
    PortfolioAnalysisResult analysisResult =
        new PortfolioAnalysisResult(
            "반도체 업종 비중이 높아 관련 이슈의 영향을 크게 받을 수 있어요.",
            List.of(
                new PortfolioAnalysisResult.AssetImpactResult(
                    "SK하이닉스",
                    ImpactDirection.NEUTRAL,
                    ImpactLevel.HIGH,
                    "반도체 수요와 비용이 함께 증가했어요.",
                    "직접적인 주가 방향은 불분명합니다.",
                    "수요와 비용 추이를 함께 확인해야 해요.",
                    "긍정 또는 부정 영향을 판단할 근거가 충분하지 않습니다.",
                    "반도체 수요가 전년 대비 증가했습니다.",
                    1)),
            List.of(new PortfolioAnalysisResult.Source("한국거래소", "https://example.com/news")));
    given(portfolioAnalysisAiClient.analyze(targets)).willReturn(analysisResult);

    PortfolioAnalysisResponse response = portfolioAnalysisService.analyze(request);

    assertThat(response.totalAssetCount()).isEqualTo(2);
    assertThat(response.analyzedAssetCount()).isEqualTo(1);
    assertThat(response.overallDirection()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(response.overallScore()).isEqualByComparingTo("0.00");
    assertThat(response.positiveImpactScore()).isEqualByComparingTo("0.00");
    assertThat(response.negativeImpactScore()).isEqualByComparingTo("0.00");
    assertThat(response.overallImpact()).contains("반도체 업종 비중");
    assertThat(response.impacts()).hasSize(1);
    assertThat(response.impacts().get(0).assetName()).isEqualTo("SK하이닉스");
    assertThat(response.impacts().get(0).weight()).isEqualByComparingTo("22");
    assertThat(response.impacts().get(0).direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(response.impacts().get(0).impactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(response.impacts().get(0).issueSummary()).contains("반도체 수요");
    assertThat(response.impacts().get(0).outlook()).contains("추이");
    assertThat(response.impacts().get(0).evidenceSentence()).isEqualTo("반도체 수요가 전년 대비 증가했습니다.");
    assertThat(response.impacts().get(0).rank()).isEqualTo(1);
    assertThat(response.sources())
        .containsExactly(new PortfolioAnalysisSourceResponse("한국거래소", "https://example.com/news"));
    verify(portfolioAnalysisAiClient).analyze(targets);
  }

  @Test
  @DisplayName("영향도순으로 정렬된 전체 자산의 분석 결과를 반환한다")
  void analyzeReturnsAllImpacts() {
    PortfolioAnalysisRequest request =
        request(
            asset("삼성전자", "30"), asset("SK하이닉스", "25"), asset("현대차", "20"), asset("NAVER", "25"));
    List<PortfolioAnalysisTarget> targets =
        List.of(
            new PortfolioAnalysisTarget("삼성전자", new BigDecimal("30")),
            new PortfolioAnalysisTarget("SK하이닉스", new BigDecimal("25")),
            new PortfolioAnalysisTarget("현대차", new BigDecimal("20")),
            new PortfolioAnalysisTarget("NAVER", new BigDecimal("25")));
    PortfolioAnalysisResult analysisResult =
        new PortfolioAnalysisResult(
            "전체 포트폴리오 영향",
            List.of(
                impact("삼성전자", ImpactLevel.HIGH, 1),
                impact("SK하이닉스", ImpactLevel.HIGH, 2),
                impact("현대차", ImpactLevel.MEDIUM, 3),
                impact("NAVER", ImpactLevel.LOW, 4)),
            List.of(new PortfolioAnalysisResult.Source("출처", "https://example.com")));
    given(portfolioAnalysisAiClient.analyze(targets)).willReturn(analysisResult);

    PortfolioAnalysisResponse response = portfolioAnalysisService.analyze(request);

    assertThat(response.totalAssetCount()).isEqualTo(4);
    assertThat(response.analyzedAssetCount()).isEqualTo(4);
    assertThat(response.impacts())
        .extracting(impact -> impact.assetName())
        .containsExactly("삼성전자", "SK하이닉스", "현대차", "NAVER");
    assertThat(response.impacts()).extracting(impact -> impact.rank()).containsExactly(1, 2, 3, 4);
  }

  @Test
  @DisplayName("포트폴리오가 비어 있으면 AI를 호출하지 않고 빈 분석 결과를 반환한다")
  void analyzeReturnsEmptyResponseWhenPortfolioIsEmpty() {
    PortfolioAnalysisResponse response = portfolioAnalysisService.analyze(request());

    assertThat(response.totalAssetCount()).isZero();
    assertThat(response.analyzedAssetCount()).isZero();
    assertThat(response.overallDirection()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(response.overallScore()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.impacts()).isEmpty();
    verifyNoInteractions(portfolioAnalysisAiClient);
  }

  @Test
  @DisplayName("동일한 포트폴리오를 다시 요청해도 매번 AI로 분석한다")
  void analyzeEveryRequest() {
    PortfolioAnalysisRequest request = request(asset("삼성전자", "100"));
    List<PortfolioAnalysisTarget> targets =
        List.of(new PortfolioAnalysisTarget("삼성전자", new BigDecimal("100")));
    given(portfolioAnalysisAiClient.analyze(targets))
        .willReturn(new PortfolioAnalysisResult("분석 결과", List.of(), List.of()));

    portfolioAnalysisService.analyze(request);
    portfolioAnalysisService.analyze(request);

    verify(portfolioAnalysisAiClient, times(2)).analyze(targets);
  }

  @Test
  @DisplayName("동일한 자산을 중복 요청하면 조회와 AI 분석 전에 예외를 던진다")
  void analyzeThrowsWhenPortfolioAssetIsDuplicated() {
    PortfolioAnalysisRequest request = request(asset(" NAVER ", "50"), asset("naver", "50"));

    assertThatThrownBy(() -> portfolioAnalysisService.analyze(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    verifyNoInteractions(portfolioAnalysisAiClient);
  }

  @Test
  @DisplayName("포트폴리오 보유 비중의 합이 100이 아니면 분석 전에 예외를 던진다")
  void analyzeThrowsWhenTotalWeightIsNotOneHundred() {
    PortfolioAnalysisRequest request = request(asset("삼성전자", "60"), asset("현대차", "30"));

    assertThatThrownBy(() -> portfolioAnalysisService.analyze(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.INVALID_PORTFOLIO_WEIGHT_TOTAL);
    verifyNoInteractions(portfolioAnalysisAiClient);
  }

  private PortfolioAnalysisRequest request(PortfolioAssetRequest... assets) {
    return new PortfolioAnalysisRequest(List.of(assets));
  }

  private PortfolioAssetRequest asset(String assetName, String weight) {
    return new PortfolioAssetRequest(assetName, new BigDecimal(weight));
  }

  private PortfolioAnalysisResult.AssetImpactResult impact(
      String assetName, ImpactLevel impactLevel, int sortOrder) {
    return new PortfolioAnalysisResult.AssetImpactResult(
        assetName,
        ImpactDirection.NEUTRAL,
        impactLevel,
        "관련 이슈 요약",
        "예상 반응",
        "향후 방향성",
        "판단 근거",
        "판단에 사용한 검색 근거",
        sortOrder);
  }
}
