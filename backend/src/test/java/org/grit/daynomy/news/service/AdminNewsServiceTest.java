package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminNewsServiceTest {

  @Mock private NewsRepository newsRepository;

  @InjectMocks private AdminNewsService adminNewsService;

  @Test
  @DisplayName("관리자 뉴스 등록은 수동 출처의 초안으로 저장한다")
  void createNewsSavesManualDraft() {
    AdminNewsCreateRequest request =
        new AdminNewsCreateRequest(
            "뉴스 제목",
            "뉴스 본문",
            "뉴스 요약",
            "https://example.com/image.png",
            "https://example.com/news/1",
            Category.STOCK);
    given(newsRepository.save(any(News.class))).willAnswer(invocation -> invocation.getArgument(0));

    News savedNews = adminNewsService.createDraft(request);

    ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
    verify(newsRepository).save(newsCaptor.capture());
    News capturedNews = newsCaptor.getValue();
    assertThat(savedNews).isSameAs(capturedNews);
    assertThat(capturedNews.getTitle()).isEqualTo("뉴스 제목");
    assertThat(capturedNews.getContent()).isEqualTo("뉴스 본문");
    assertThat(capturedNews.getSource()).isNull();
    assertThat(capturedNews.getExternalId()).isNull();
    assertThat(capturedNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
    assertThat(capturedNews.getPublishedAt()).isNull();
  }

  @Test
  @DisplayName("관리자 뉴스 목록을 상태와 함께 페이지로 조회한다")
  void getNewsPageReturnsNewsWithStatus() {
    News news =
        News.createAdminDraft(
            "초안 뉴스", "뉴스 본문", "뉴스 요약", null, "https://example.com/news/1", Category.STOCK);
    PageRequest pageable = PageRequest.of(0, 15);
    given(newsRepository.findAdminNews(null, null, pageable))
        .willReturn(new PageImpl<>(List.of(news), pageable, 1));

    var response = adminNewsService.getNewsPage(1, 15, null, null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().title()).isEqualTo("초안 뉴스");
    assertThat(response.items().getFirst().status()).isEqualTo(NewsStatus.DRAFT);
    verify(newsRepository).findAdminNews(null, null, pageable);
  }

  @Test
  @DisplayName("관리자 뉴스 상세는 발행되지 않은 뉴스도 조회한다")
  void getNewsDetailReturnsDraftNews() {
    News news =
        News.createAdminDraft(
            "초안 뉴스", "뉴스 본문", "뉴스 요약", null, "https://example.com/news/1", Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    News foundNews = adminNewsService.getNewsDetail(1L);

    assertThat(foundNews).isSameAs(news);
    assertThat(foundNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
  }

  @Test
  @DisplayName("관리자 뉴스 내용을 수정하고 기존 상태는 유지한다")
  void updateNewsChangesContentWithoutChangingStatus() {
    News news =
        News.createAdminDraft(
            "기존 제목", "기존 본문", "기존 요약", null, "https://example.com/old", Category.STOCK);
    AdminNewsUpdateRequest request =
        new AdminNewsUpdateRequest(
            "수정 제목", "수정 본문", "수정 요약", "new-image.png", "https://example.com/new", Category.BOND);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    News updatedNews = adminNewsService.update(1L, request);

    assertThat(updatedNews.getTitle()).isEqualTo("수정 제목");
    assertThat(updatedNews.getContent()).isEqualTo("수정 본문");
    assertThat(updatedNews.getDescription()).isEqualTo("수정 요약");
    assertThat(updatedNews.getImageUrl()).isEqualTo("new-image.png");
    assertThat(updatedNews.getSourceUrl()).isEqualTo("https://example.com/new");
    assertThat(updatedNews.getCategory()).isEqualTo(Category.BOND);
    assertThat(updatedNews.getStatus()).isEqualTo(NewsStatus.DRAFT);
  }

  @Test
  @DisplayName("존재하지 않는 뉴스 수정 요청은 예외를 던진다")
  void updateNewsThrowsWhenNewsIsMissing() {
    given(newsRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                adminNewsService.update(
                    1L,
                    new AdminNewsUpdateRequest(
                        "수정 제목", "수정 본문", null, null, "https://example.com/new", Category.BOND)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
  }

  @Test
  @DisplayName("관리자 뉴스 삭제는 삭제 상태로 변경한다")
  void deleteNewsChangesStatusToDeleted() {
    News news =
        News.createAdminDraft(
            "뉴스 제목", "뉴스 본문", "뉴스 요약", null, "https://example.com/news/1", Category.STOCK);
    given(newsRepository.findById(1L)).willReturn(Optional.of(news));

    adminNewsService.delete(1L);

    assertThat(news.getStatus()).isEqualTo(NewsStatus.DELETED);
    assertThat(news.getPublishedAt()).isNull();
  }
}
