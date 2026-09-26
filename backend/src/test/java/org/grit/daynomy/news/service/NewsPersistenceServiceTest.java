package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsPersistenceServiceTest {

  @Mock private NewsRepository newsRepository;

  @InjectMocks private NewsPersistenceService newsPersistenceService;

  @Test
  @DisplayName("경제 뉴스 초안을 저장한다")
  void saveDraftsSavesNews() {
    GeneratedEconomicNews article = new GeneratedEconomicNews("제목", "본문", Category.STOCK, List.of());
    given(newsRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));

    List<News> drafts =
        newsPersistenceService.saveDrafts(List.of(article), Collections.singletonList(null));

    ArgumentCaptor<List<News>> newsCaptor = ArgumentCaptor.forClass(List.class);
    verify(newsRepository).saveAll(newsCaptor.capture());
    assertThat(drafts).hasSize(1);
    assertThat(newsCaptor.getValue().getFirst().getTitle()).isEqualTo("제목");
    assertThat(newsCaptor.getValue().getFirst().getSources()).isEmpty();
    assertThat(newsCaptor.getValue().getFirst().getImageUrl()).isNull();
  }
}
