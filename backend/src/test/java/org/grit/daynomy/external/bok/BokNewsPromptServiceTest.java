package org.grit.daynomy.external.bok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.grit.daynomy.external.bok.dto.BokStatisticItem;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BokNewsPromptServiceTest {

  @Mock private BokClient bokClient;

  @Test
  @DisplayName("한국은행 ECOS 지표의 최근 2개 시점을 뉴스 프롬프트로 변환한다")
  void createPrompts() {
    var indicator = indicator("722Y001", "M");
    BokNewsPromptService service =
        new BokNewsPromptService(
            bokClient, new BokProperties("test-key", "https://ecos.example", List.of(indicator)));
    given(bokClient.getRecentData(indicator))
        .willReturn(
            List.of(
                new BokStatisticItem(
                    "722Y001", "한국은행 기준금리 및 여수신금리", "0101000", "한국은행 기준금리", "연%", "202606", "2.50"),
                new BokStatisticItem(
                    "722Y001",
                    "한국은행 기준금리 및 여수신금리",
                    "0101000",
                    "한국은행 기준금리",
                    "연%",
                    "202607",
                    "2.75")));

    var prompts = service.createPrompts();

    assertThat(prompts).hasSize(1);
    assertThat(prompts.getFirst().source()).isEqualTo(NewsSource.BOK);
    assertThat(prompts.getFirst().category()).isEqualTo(Category.ECONOMY);
    assertThat(prompts.getFirst().externalId()).isEqualTo("base-rate:202607");
    assertThat(prompts.getFirst().instruction())
        .contains(
            "경제 전문 기자",
            "JSON 형식으로만",
            "불릿, 번호 목록, 표",
            "정확히 2~4개 문단",
            "빈 줄 하나(\\n\\n)",
            "한국은행 ECOS에 따르면")
        .doesNotContain("2.75", "[한국은행 ECOS 참고 데이터]");
    assertThat(prompts.getFirst().sourceData())
        .contains("[한국은행 ECOS 참고 데이터]", "2.75", "증감률", "조회일자")
        .doesNotContain("경제 전문 기자");
  }

  @Test
  @DisplayName("통계표 코드가 없으면 뉴스 프롬프트 생성을 건너뛴다")
  void createPromptsSkipsBlankStatisticCode() {
    var indicator = indicator("", "M");
    BokNewsPromptService service =
        new BokNewsPromptService(
            bokClient, new BokProperties("test-key", "https://ecos.example", List.of(indicator)));

    var prompts = service.createPrompts();

    assertThat(prompts).isEmpty();
    verify(bokClient, never()).getRecentData(indicator);
  }

  private BokProperties.Indicator indicator(String statisticCode, String cycle) {
    return new BokProperties.Indicator(
        "base-rate",
        "한국은행 기준금리",
        statisticCode,
        cycle,
        Category.ECONOMY,
        "한국은행",
        "한국은행 기준금리 및 여수신금리",
        "한국은행 기준금리",
        List.of("0101000"));
  }
}
