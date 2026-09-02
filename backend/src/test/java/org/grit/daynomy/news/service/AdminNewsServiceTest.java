package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
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
}
