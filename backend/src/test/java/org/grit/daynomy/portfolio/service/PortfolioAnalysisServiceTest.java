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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.bookmark.domain.Bookmark;
import org.grit.daynomy.bookmark.repository.BookmarkRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
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

  @Mock private BookmarkRepository bookmarkRepository;

  @Mock private AssetRepository assetRepository;

  @Mock private PortfolioAnalysisAiClient portfolioAnalysisAiClient;

  @InjectMocks private PortfolioAnalysisService portfolioAnalysisService;

  @Test
  @DisplayName("현재 북마크 자산을 AI로 분석하고 응답 DTO로 변환한다")
  void getPortfolioAnalysisReturnsCurrentPortfolioAnalysis() {
    News news = createNews();
    Asset firstAsset = createAsset(10L, "삼성전자", "005930");
    Asset secondAsset = createAsset(20L, "SK하이닉스", "000660");
    Bookmark firstBookmark = createBookmark(101L, firstAsset);
    Bookmark secondBookmark = createBookmark(102L, secondAsset);
    List<PortfolioAnalysisTarget> targets =
        List.of(
            new PortfolioAnalysisTarget(10L, 101L, "삼성전자", "STOCK", "005930"),
            new PortfolioAnalysisTarget(20L, 102L, "SK하이닉스", "STOCK", "000660"));
    PortfolioAnalysisResult analysisResult =
        new PortfolioAnalysisResult(
            List.of(
                new PortfolioAnalysisResult.AssetImpactResult(
                    20L,
                    102L,
                    ImpactDirection.POSITIVE,
                    ImpactLevel.HIGH,
                    "주가가 상승할 수 있습니다.",
                    "반도체 수요 증가가 예상됩니다.",
                    1)));
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.of(news));
    given(bookmarkRepository.findAllByMemberIdOrderByIdAsc(3L))
        .willReturn(List.of(firstBookmark, secondBookmark));
    given(assetRepository.findAllById(List.of(10L, 20L)))
        .willReturn(List.of(firstAsset, secondAsset));
    given(portfolioAnalysisAiClient.analyze("뉴스 본문", targets)).willReturn(analysisResult);

    PortfolioAnalysisResponse response = portfolioAnalysisService.getPortfolioAnalysis(3L, 1L);

    assertThat(response.impacts()).hasSize(1);
    assertThat(response.impacts().get(0).bookmarkId()).isEqualTo(102L);
    assertThat(response.impacts().get(0).assetId()).isEqualTo(20L);
    assertThat(response.impacts().get(0).name()).isEqualTo("SK하이닉스");
    assertThat(response.impacts().get(0).category()).isEqualTo("STOCK");
    assertThat(response.impacts().get(0).assetCode()).isEqualTo("000660");
    assertThat(response.impacts().get(0).direction()).isEqualTo(ImpactDirection.POSITIVE);
    assertThat(response.impacts().get(0).impactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(response.impacts().get(0).sortOrder()).isEqualTo(1);
    verify(portfolioAnalysisAiClient).analyze("뉴스 본문", targets);
  }

  @Test
  @DisplayName("북마크가 없으면 AI를 호출하지 않고 빈 분석 결과를 반환한다")
  void getPortfolioAnalysisReturnsEmptyResponseWhenBookmarksAreMissing() {
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED))
        .willReturn(Optional.of(createNews()));
    given(bookmarkRepository.findAllByMemberIdOrderByIdAsc(3L)).willReturn(List.of());

    PortfolioAnalysisResponse response = portfolioAnalysisService.getPortfolioAnalysis(3L, 1L);

    assertThat(response.impacts()).isEmpty();
    verifyNoInteractions(assetRepository, portfolioAnalysisAiClient);
  }

  @Test
  @DisplayName("동일한 뉴스와 포트폴리오를 다시 조회해도 매번 AI로 분석한다")
  void getPortfolioAnalysisAnalyzesEveryRequest() {
    News news = createNews();
    Asset asset = createAsset(10L, "삼성전자", "005930");
    Bookmark bookmark = createBookmark(101L, asset);
    List<PortfolioAnalysisTarget> targets =
        List.of(new PortfolioAnalysisTarget(10L, 101L, "삼성전자", "STOCK", "005930"));
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.of(news));
    given(bookmarkRepository.findAllByMemberIdOrderByIdAsc(3L)).willReturn(List.of(bookmark));
    given(assetRepository.findAllById(List.of(10L))).willReturn(List.of(asset));
    given(portfolioAnalysisAiClient.analyze("뉴스 본문", targets))
        .willReturn(new PortfolioAnalysisResult(List.of()));

    portfolioAnalysisService.getPortfolioAnalysis(3L, 1L);
    portfolioAnalysisService.getPortfolioAnalysis(3L, 1L);

    verify(portfolioAnalysisAiClient, times(2)).analyze("뉴스 본문", targets);
  }

  @Test
  @DisplayName("뉴스가 없으면 포트폴리오 분석 전에 예외를 던진다")
  void getPortfolioAnalysisThrowsWhenNewsIsMissing() {
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.empty());

    assertThatThrownBy(() -> portfolioAnalysisService.getPortfolioAnalysis(3L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
    verifyNoInteractions(bookmarkRepository, assetRepository, portfolioAnalysisAiClient);
  }

  @Test
  @DisplayName("북마크한 자산이 없으면 AI를 호출하지 않고 예외를 던진다")
  void getPortfolioAnalysisThrowsWhenBookmarkedAssetIsMissing() {
    Asset missingAsset = mock(Asset.class);
    given(missingAsset.getId()).willReturn(10L);
    Bookmark bookmark = mock(Bookmark.class);
    given(bookmark.getAsset()).willReturn(missingAsset);
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED))
        .willReturn(Optional.of(createNews()));
    given(bookmarkRepository.findAllByMemberIdOrderByIdAsc(3L)).willReturn(List.of(bookmark));
    given(assetRepository.findAllById(List.of(10L))).willReturn(List.of());

    assertThatThrownBy(() -> portfolioAnalysisService.getPortfolioAnalysis(3L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.PORTFOLIO_ASSET_NOT_FOUND);
    verify(portfolioAnalysisAiClient, never()).analyze(any(), any());
  }

  private News createNews() {
    return News.createPublished(
        "뉴스 제목",
        "뉴스 본문",
        "뉴스 설명",
        "image.png",
        NewsSource.DART,
        "external-1",
        "https://example.com/news",
        Category.STOCK,
        Instant.parse("2026-08-23T10:00:00Z"));
  }

  private Bookmark createBookmark(Long id, Asset asset) {
    Bookmark bookmark = mock(Bookmark.class);
    given(bookmark.getId()).willReturn(id);
    given(bookmark.getAsset()).willReturn(asset);
    return bookmark;
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
