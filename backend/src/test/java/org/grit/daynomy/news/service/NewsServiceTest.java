package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

  @Mock private NewsRepository newsRepository;

  @InjectMocks private NewsService newsService;

  @Test
  @DisplayName("뉴스 목록을 페이지로 조회한다")
  void findNewsReturnsPagedNews() {
    PageRequest pageable = PageRequest.of(0, 15);
    News news =
        News.createPublished(
            "title",
            "content",
            "description",
            "image.png",
            NewsSource.DART,
            "external-1",
            "https://example.com/1",
            Category.STOCK,
            Instant.parse("2026-08-17T10:00:00Z"));
    given(newsRepository.findByStatusOrderByPublishedAtDescIdDesc(NewsStatus.PUBLISHED, pageable))
        .willReturn(new PageImpl<>(java.util.List.of(news), pageable, 1));

    var response = newsService.getNewsPage(1, 15, null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("카테고리로 뉴스 목록을 필터링한다")
  void findNewsFiltersByCategory() {
    PageRequest pageable = PageRequest.of(1, 15);
    given(
            newsRepository.findByStatusAndCategoryOrderByPublishedAtDescIdDesc(
                NewsStatus.PUBLISHED, Category.REAL_ESTATE, pageable))
        .willReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

    newsService.getNewsPage(2, 15, Category.REAL_ESTATE);

    verify(newsRepository)
        .findByStatusAndCategoryOrderByPublishedAtDescIdDesc(
            NewsStatus.PUBLISHED, Category.REAL_ESTATE, pageable);
  }

  @Test
  @DisplayName("오늘 발행된 뉴스 중 최신 9건을 조회한다")
  void findTodayNewsReturnsLatestNewsPublishedToday() {
    LocalDate today = LocalDate.now();
    Instant startInclusive = startOfDay(today);
    Instant endExclusive = startOfDay(today.plusDays(1));
    News news =
        News.createPublished(
            "today news",
            "content",
            "description",
            "image.png",
            NewsSource.DART,
            "external-1",
            "https://example.com/1",
            Category.STOCK,
            atHour(today, 10));
    PageRequest pageable = PageRequest.of(0, 9);
    given(
            newsRepository
                .findByStatusAndPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
                    NewsStatus.PUBLISHED, startInclusive, endExclusive, pageable))
        .willReturn(new PageImpl<>(java.util.List.of(news), pageable, 1));

    var response = newsService.getTodayNews();

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().title()).isEqualTo("today news");
    verify(newsRepository)
        .findByStatusAndPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
            NewsStatus.PUBLISHED, startInclusive, endExclusive, pageable);
  }

  @Test
  @DisplayName("오늘의 뉴스가 없으면 빈 목록을 반환한다")
  void findTodayNewsReturnsNullWhenMissing() {
    LocalDate today = LocalDate.now();
    Instant startInclusive = startOfDay(today);
    Instant endExclusive = startOfDay(today.plusDays(1));
    PageRequest pageable = PageRequest.of(0, 9);
    given(
            newsRepository
                .findByStatusAndPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
                    NewsStatus.PUBLISHED, startInclusive, endExclusive, pageable))
        .willReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

    assertThat(newsService.getTodayNews().items()).isEmpty();
  }

  @Test
  @DisplayName("뉴스 상세를 조회한다")
  void findNewsDetailReturnsNews() {
    News news =
        News.createPublished(
            "title",
            "content",
            "description",
            "image.png",
            NewsSource.DART,
            "external-1",
            "https://example.com/1",
            Category.STOCK,
            Instant.parse("2026-08-17T10:00:00Z"));
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.of(news));

    var response = newsService.getNewsDetail(1L);

    assertThat(response.title()).isEqualTo("title");
    assertThat(response.category()).isEqualTo(Category.STOCK);
  }

  @Test
  @DisplayName("뉴스 상세가 없으면 예외를 던진다")
  void findNewsDetailThrowsWhenMissing() {
    given(newsRepository.findByIdAndStatus(1L, NewsStatus.PUBLISHED)).willReturn(Optional.empty());

    assertThatThrownBy(() -> newsService.getNewsDetail(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
  }

  private static Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
  }

  private static Instant atHour(LocalDate date, int hour) {
    return date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant();
  }
}
