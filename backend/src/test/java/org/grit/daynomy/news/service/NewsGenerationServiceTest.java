package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import org.grit.daynomy.external.openai.OpenAiNewsGenerator;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsGenerationServiceTest {

  @Mock private OpenAiNewsGenerator openAiNewsGenerator;
  @Mock private NewsPersistenceService newsPersistenceService;

  @InjectMocks private NewsGenerationService newsGenerationService;

  @Test
  @DisplayName("경제 뉴스 초안은 이미지를 생성하지 않고 이미지 URL 없이 저장한다")
  void generateEconomyNewsDraftsSavesWithoutImages() {
    GeneratedEconomicNews article =
        new GeneratedEconomicNews("경제 뉴스", "본문", Category.STOCK, List.of());
    given(openAiNewsGenerator.generateEconomicNews()).willReturn(List.of(article));
    given(newsPersistenceService.saveDrafts(List.of(article), Collections.singletonList(null)))
        .willReturn(List.of());

    List<News> drafts = newsGenerationService.generateEconomyNewsDrafts();

    assertThat(drafts).isEmpty();
    verify(newsPersistenceService).saveDrafts(List.of(article), Collections.singletonList(null));
  }
}
