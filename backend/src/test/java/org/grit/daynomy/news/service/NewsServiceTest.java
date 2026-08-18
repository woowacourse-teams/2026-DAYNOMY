package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.CommonErrorCode;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
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
        new News(
            "title",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 10, 0));
    given(newsRepository.findAllByOrderByPublishedAtDescIdDesc(pageable))
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
    given(newsRepository.findByCategoryOrderByPublishedAtDescIdDesc(Category.REAL_ESTATE, pageable))
        .willReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

    newsService.getNewsPage(2, 15, Category.REAL_ESTATE);

    verify(newsRepository)
        .findByCategoryOrderByPublishedAtDescIdDesc(Category.REAL_ESTATE, pageable);
  }

  @Test
  @DisplayName("잘못된 페이지 요청이면 예외를 던진다")
  void findNewsRejectsInvalidPage() {
    assertThatThrownBy(() -> newsService.getNewsPage(0, 15, null))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(CommonErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("뉴스 상세를 조회한다")
  void findNewsDetailReturnsNews() {
    News news =
        new News(
            "title",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 10, 0));
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    var response = newsService.getNewsDetail(1L);

    assertThat(response.title()).isEqualTo("title");
    assertThat(response.category()).isEqualTo(Category.STOCK);
  }

  @Test
  @DisplayName("뉴스 상세가 없으면 예외를 던진다")
  void findNewsDetailThrowsWhenMissing() {
    given(newsRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> newsService.getNewsDetail(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
  }
}
