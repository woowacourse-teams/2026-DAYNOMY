package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PortfolioAnalysisProseTest {
  @Test
  void addsMissingFinalPeriodWithoutChangingMeaning() {
    assertThat(PortfolioAnalysisProse.normalize("  실적이 개선됐어요  ")).isEqualTo("실적이 개선됐어요.");
  }

  @Test
  void preservesParagraphsDatesAndDecimalNumbers() {
    String text = "10.12 발표에서 이익이 3.5% 늘었어요. 비용도 증가했어요.\n\n다음 실적을 확인해 보세요";
    assertThat(PortfolioAnalysisProse.normalize(text)).isEqualTo(text + ".");
  }

  @Test
  void rejectsMixedToneEvenWhenLastSentenceUsesFriendlyTone() {
    for (String text :
        new String[] {
          "이익이 감소했습니다. 비용도 증가했어요.",
          "이익 감소함. 비용도 증가했어요.",
          "이익이 감소했다.\n\n비용도 증가했어요.",
          "실적이 좋아요!",
          "실적이 좋아요?",
          "실적이 좋아요...",
          ""
        }) {
      assertThatIllegalArgumentException().isThrownBy(() -> PortfolioAnalysisProse.normalize(text));
    }
  }

  @Test
  void acceptsDifferentNaturalFriendlyEndings() {
    String text = "수요가 늘었어요. 비용이 변수예요. 수익성 개선이 관건이에요. 다음 발표를 확인해 보세요.";
    assertThat(PortfolioAnalysisProse.normalize(text)).isEqualTo(text);
  }
}
