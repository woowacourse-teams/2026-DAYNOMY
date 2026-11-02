package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.NewsDetailResponse;
import org.grit.daynomy.news.service.NewsService;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.dto.ContentSearchItemResponse;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.service.ContentSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class LeagueReviewEvidenceServiceTest {
  private final AssetContentRepository links = mock(AssetContentRepository.class);
  private final ContentSearchService search = mock(ContentSearchService.class);
  private final NewsService news = mock(NewsService.class);
  private final Instant now = Instant.parse("2026-10-28T00:00:00Z");
  private final LeagueReviewEvidenceService service =
      new LeagueReviewEvidenceService(links, search, news, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void retrievesOnlyPublishedArticleBodiesAfterTheDecisionAndBuildsServerOwnedLinks() {
    Instant saved = Instant.parse("2026-10-20T00:00:00Z");
    given(
            links.findPublishedNewsIdsForAsset(
                2L, NewsStatus.PUBLISHED, saved, now, PageRequest.of(0, 20)))
        .willReturn(List.of(1L, 2L));
    given(search.search("삼성전자 매출", ContentSearchSource.DAYNOMY, 1, 20, ContentSearchSort.LATEST))
        .willReturn(
            new ContentSearchResponse(
                List.of(item("ASSET_CONTENT", 3L), item("NEWS", 2L), item("NEWS", 4L)),
                1,
                20,
                3,
                1));
    given(news.getNewsDetail(1L)).willReturn(detail(1L, "이전 기사", "<p>지난 기사</p>", saved));
    given(news.getNewsDetail(2L))
        .willReturn(detail(2L, "실적 기사", "<p>매출이 증가했습니다.</p>", now.minusSeconds(3600)));
    given(news.getNewsDetail(4L))
        .willReturn(detail(4L, "미래 기사", "<p>미래 기사</p>", now.plusSeconds(60)));

    var result = service.search(2L, "삼성전자", saved, "매출");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().evidence().id()).isEqualTo("NEWS:2");
    assertThat(result.getFirst().evidence().href()).isEqualTo("/news/2");
    assertThat(result.getFirst().evidence().excerpt()).isEqualTo("매출이 증가했습니다.");
    verify(news).getNewsDetail(2L);
  }

  @Test
  void limitsOldRecordsToNinetyDaysAndBoundsExcerpts() {
    Instant cutoff = now.minusSeconds(90L * 24 * 3600);
    given(
            links.findPublishedNewsIdsForAsset(
                2L, NewsStatus.PUBLISHED, cutoff, now, PageRequest.of(0, 20)))
        .willReturn(List.of(2L));
    given(search.search("삼성전자", ContentSearchSource.DAYNOMY, 1, 20, ContentSearchSort.LATEST))
        .willReturn(new ContentSearchResponse(List.of(), 1, 20, 0, 0));
    given(news.getNewsDetail(2L))
        .willReturn(detail(2L, "기사", "<p>" + "매출 ".repeat(300) + "</p>", now.minusSeconds(3600)));

    var result = service.search(2L, "삼성전자", now.minusSeconds(120L * 24 * 3600), "");

    assertThat(result.getFirst().evidence().excerpt()).hasSize(160);
    assertThat(result.getFirst().modelContext()).hasSize(800);
  }

  private ContentSearchItemResponse item(String kind, Long id) {
    return new ContentSearchItemResponse(
        kind, id, ContentSearchSource.DAYNOMY, "기사", now, null, null, null, null, null, null, null);
  }

  private NewsDetailResponse detail(Long id, String title, String body, Instant publishedAt) {
    return new NewsDetailResponse(id, title, body, null, null, List.of(), null, publishedAt);
  }
}
