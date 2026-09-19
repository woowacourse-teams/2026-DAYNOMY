package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
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

  @Mock private NewsRepository newsRepository;

  @Mock private AssetRepository assetRepository;

  @Mock private PortfolioAnalysisAiClient portfolioAnalysisAiClient;

  @InjectMocks private PortfolioAnalysisService portfolioAnalysisService;

  @Test
  @DisplayName("요청한 포트폴리오 자산을 AI로 분석하고 응답 DTO로 변환한다")
  void analyzeReturnsPortfolioAnalysis() {
    News news = createNews();
    Asset firstAsset = createAsset(10L, "삼성전자", "005930");
    Asset secondAsset = createAsset(20L, "SK하이닉스", "000660");
    PortfolioAnalysisRequest request = request(asset(10L, "30"), asset(20L, "22"));
    List<PortfolioAnalysisTarget> targets =
        List.of(
            new PortfolioAnalysisTarget(10L, "삼성전자", "STOCK", "005930"),
            new PortfolioAnalysisTarget(20L, "SK하이닉스", "STOCK", "000660"));
    PortfolioAnalysisResult analysisResult =
        new PortfolioAnalysisResult(
            List.of(
                new PortfolioAnalysisResult.AssetImpactResult(
                    20L,
                    ImpactDirection.NEUTRAL,
                    ImpactLevel.HIGH,
                    "직접적인 주가 방향은 불분명합니다.",
                    "긍정 또는 부정 영향을 판단할 근거가 충분하지 않습니다.",
                    1)));
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.of(news));
    given(assetRepository.findAllById(List.of(10L, 20L)))
        .willReturn(List.of(firstAsset, secondAsset));
    given(portfolioAnalysisAiClient.analyze("뉴스 본문", targets)).willReturn(analysisResult);

    PortfolioAnalysisResponse response = portfolioAnalysisService.analyze(1L, request);

    assertThat(response.totalAssetCount()).isEqualTo(2);
    assertThat(response.analyzedAssetCount()).isEqualTo(1);
    assertThat(response.impacts()).hasSize(1);
    assertThat(response.impacts().get(0).assetId()).isEqualTo(20L);
    assertThat(response.impacts().get(0).assetName()).isEqualTo("SK하이닉스");
    assertThat(response.impacts().get(0).category()).isEqualTo("STOCK");
    assertThat(response.impacts().get(0).assetCode()).isEqualTo("000660");
    assertThat(response.impacts().get(0).weight()).isEqualByComparingTo("22");
    assertThat(response.impacts().get(0).direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(response.impacts().get(0).impactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(response.impacts().get(0).rank()).isEqualTo(1);
    verify(portfolioAnalysisAiClient).analyze("뉴스 본문", targets);
  }

  @Test
  @DisplayName("포트폴리오가 비어 있으면 AI를 호출하지 않고 빈 분석 결과를 반환한다")
  void analyzeReturnsEmptyResponseWhenPortfolioIsEmpty() {
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED))
        .willReturn(Optional.of(createNews()));

    PortfolioAnalysisResponse response = portfolioAnalysisService.analyze(1L, request());

    assertThat(response.totalAssetCount()).isZero();
    assertThat(response.analyzedAssetCount()).isZero();
    assertThat(response.impacts()).isEmpty();
    verifyNoInteractions(assetRepository, portfolioAnalysisAiClient);
  }

  @Test
  @DisplayName("동일한 뉴스와 포트폴리오를 다시 요청해도 매번 AI로 분석한다")
  void analyzeEveryRequest() {
    News news = createNews();
    Asset asset = createAsset(10L, "삼성전자", "005930");
    PortfolioAnalysisRequest request = request(asset(10L, "30"));
    List<PortfolioAnalysisTarget> targets =
        List.of(new PortfolioAnalysisTarget(10L, "삼성전자", "STOCK", "005930"));
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.of(news));
    given(assetRepository.findAllById(List.of(10L))).willReturn(List.of(asset));
    given(portfolioAnalysisAiClient.analyze("뉴스 본문", targets))
        .willReturn(new PortfolioAnalysisResult(List.of()));

    portfolioAnalysisService.analyze(1L, request);
    portfolioAnalysisService.analyze(1L, request);

    verify(portfolioAnalysisAiClient, times(2)).analyze("뉴스 본문", targets);
  }

  @Test
  @DisplayName("뉴스가 없으면 포트폴리오 분석 전에 예외를 던진다")
  void analyzeThrowsWhenNewsIsMissing() {
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.empty());
    PortfolioAnalysisRequest request = request(asset(10L, "30"));

    assertThatThrownBy(() -> portfolioAnalysisService.analyze(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
    verifyNoInteractions(assetRepository, portfolioAnalysisAiClient);
  }

  @Test
  @DisplayName("요청한 자산이 없으면 AI를 호출하지 않고 예외를 던진다")
  void analyzeThrowsWhenPortfolioAssetIsMissing() {
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED))
        .willReturn(Optional.of(createNews()));
    given(assetRepository.findAllById(List.of(10L))).willReturn(List.of());
    PortfolioAnalysisRequest request = request(asset(10L, "30"));

    assertThatThrownBy(() -> portfolioAnalysisService.analyze(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.PORTFOLIO_ASSET_NOT_FOUND);
    verify(portfolioAnalysisAiClient, never()).analyze(any(), any());
  }

  @Test
  @DisplayName("동일한 자산을 중복 요청하면 조회와 AI 분석 전에 예외를 던진다")
  void analyzeThrowsWhenPortfolioAssetIsDuplicated() {
    PortfolioAnalysisRequest request = request(asset(10L, "30"), asset(10L, "20"));

    assertThatThrownBy(() -> portfolioAnalysisService.analyze(1L, request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    verifyNoInteractions(newsRepository, assetRepository, portfolioAnalysisAiClient);
  }

  private PortfolioAnalysisRequest request(PortfolioAssetRequest... assets) {
    return new PortfolioAnalysisRequest(List.of(assets));
  }

  private PortfolioAssetRequest asset(Long assetId, String weight) {
    return new PortfolioAssetRequest(assetId, new BigDecimal(weight));
  }

  private News createNews() {
    return News.createPublished(
        "뉴스 제목",
        "뉴스 본문",
        "image.png",
        List.of(new NewsSourceInfo("DART", "https://example.com/news")),
        Category.STOCK,
        Instant.parse("2026-08-23T10:00:00Z"));
  }

  private Asset createAsset(Long id, String name, String assetCode) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    given(asset.getName()).willReturn(name);
    given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    given(asset.getAssetCode()).willReturn(assetCode);
    return asset;
  }
}
