package org.grit.daynomy.external.kosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.grit.daynomy.external.kosis.dto.KosisDataItem;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KosisNewsPromptServiceTest {

  @Mock private KosisClient kosisClient;

  @Test
  @DisplayName("국내 KOSIS 지표의 최근 2개 시점을 뉴스 프롬프트로 변환한다")
  void createPrompts() {
    var indicator = indicator("MT_ZTITLE");
    KosisNewsPromptService service =
        new KosisNewsPromptService(
            kosisClient,
            new KosisProperties("test-key", "https://kosis.example", List.of(indicator)));
    given(kosisClient.getRecentData(indicator))
        .willReturn(
            List.of(
                new KosisDataItem("소비자물가지수", "총지수", "2020=100", "202606", "112.40", null),
                new KosisDataItem("소비자물가지수", "총지수", "2020=100", "202607", "113.42", null)));

    var prompts = service.createPrompts();

    assertThat(prompts).hasSize(1);
    assertThat(prompts.getFirst().source()).isEqualTo(NewsSource.KOSIS);
    assertThat(prompts.getFirst().category()).isEqualTo(Category.ECONOMY);
    assertThat(prompts.getFirst().externalId()).isEqualTo("consumer-price-index:202607");
    assertThat(prompts.getFirst().instruction())
        .contains(
            "경제 전문 기자", "JSON 형식으로만", "불릿, 번호 목록, 표", "정확히 2~4개 문단", "빈 줄 하나(\\n\\n)", "KOSIS에 따르면")
        .doesNotContain("113.42", "[KOSIS 참고 데이터]");
    assertThat(prompts.getFirst().sourceData())
        .contains("[KOSIS 참고 데이터]", "113.42", "증감률", "조회일자")
        .doesNotContain("경제 전문 기자");
  }

  @Test
  @DisplayName("국제통계와 북한통계는 뉴스 프롬프트 생성을 건너뛴다")
  void createPromptsSkipsRestrictedViews() {
    var indicator = indicator("MT_RTITLE");
    KosisNewsPromptService service =
        new KosisNewsPromptService(
            kosisClient,
            new KosisProperties("test-key", "https://kosis.example", List.of(indicator)));

    var prompts = service.createPrompts();

    assertThat(prompts).isEmpty();
    verify(kosisClient, never()).getRecentData(indicator);
  }

  @Test
  @DisplayName("userStatsId가 없으면 뉴스 프롬프트 생성을 건너뛴다")
  void createPromptsSkipsBlankUserStatsId() {
    var indicator =
        new KosisProperties.Indicator(
            "consumer-price-index",
            "소비자물가지수",
            "MT_ZTITLE",
            "",
            "M",
            Category.ECONOMY,
            "국가데이터처",
            "소비자물가조사",
            "소비자물가지수");
    KosisNewsPromptService service =
        new KosisNewsPromptService(
            kosisClient,
            new KosisProperties("test-key", "https://kosis.example", List.of(indicator)));

    var prompts = service.createPrompts();

    assertThat(prompts).isEmpty();
    verify(kosisClient, never()).getRecentData(indicator);
  }

  private KosisProperties.Indicator indicator(String viewCode) {
    return new KosisProperties.Indicator(
        "consumer-price-index",
        "소비자물가지수",
        viewCode,
        "sample-user-stats-id",
        "M",
        Category.ECONOMY,
        "국가데이터처",
        "소비자물가조사",
        "소비자물가지수");
  }
}
