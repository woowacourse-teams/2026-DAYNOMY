package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
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
          "이익이 감소하였다. 비용도 증가했어요.",
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

  @Test
  void rejectsEnglishWithKoreanSuffixAndUnicodeEllipsis() {
    for (String text :
        List.of(
            "POSCO Future M signs KRW 1.1 trillion LFP Cathode Material Supply Contract with SK On… Order wins gather pace..요.",
            "POSCO Future M signs a supply contract 해요.",
            "Order wins grow요.",
            "실적이 좋아요… 다음 발표를 봐요.")) {
      assertThatIllegalArgumentException().isThrownBy(() -> PortfolioAnalysisProse.normalize(text));
    }
  }

  @Test
  void permitsNamesAbbreviationsAndNumbersInKoreanSentences() {
    String text = "SK하이닉스의 HBM 수요가 3.5% 늘었어요. LNG와 AI 투자 비용도 확인해야 해요.";
    assertThat(PortfolioAnalysisProse.normalize(text)).isEqualTo(text);
  }

  @Test
  void permitsAnEnglishAwardNameInsideAKoreanExplanation() {
    String text = "IONIQ 6 N이 German Car of the Year의 Performance 부문을 수상했다고 발표했어요.";
    assertThat(PortfolioAnalysisProse.normalize(text)).isEqualTo(text);
  }

  @Test
  void stillRejectsEnglishSentencesAndTitleCaseTextWithOnlyAFriendlySuffix() {
    for (String text :
        List.of(
            "German Car of the Year 해요.",
            "Samsung Electronics Reports Higher Quarterly Earnings 해요.",
            "Samsung Electronics reports higher quarterly earnings라고 발표했어요.")) {
      assertThatIllegalArgumentException().isThrownBy(() -> PortfolioAnalysisProse.normalize(text));
    }
  }

  @Test
  void rejectsEventDateNotPresentInConnectedEvidence() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                PortfolioAnalysisProse.requireSupportedDates(
                    "2026-07-30에 실적을 발표했어요.", "2026-03-24 희토류 사업 발표"));
    PortfolioAnalysisProse.requireSupportedDates(
        "2026-03-24에 사업 계획을 발표했어요.", "2026-03-24 희토류 사업 발표");
  }

  @Test
  void repairsMeasuredFormalEndingWithoutChangingFactsOrUncertainty() {
    String original =
        "분기 실적 발표 자체는 회사의 영업 현황을 확인시켜 주는 정보지만, 사업별 실적이 일부 부문에서 상쇄되는 점이 있어 단일 방향의 영향으로 해석하기 어렵습니다. 향후 분기 실적 흐름과 규제·AI 관련 비용 추이를 확인할 필요가 있어 불확실성이 존재해요.";
    assertThat(PortfolioAnalysisProse.normalize(original))
        .isEqualTo(original.replace("어렵습니다.", "어려워요."));
    assertThat(PortfolioAnalysisProse.normalize("매출이 3.5% 감소했습니다. 비용 부담이 확대됩니다. 불확실성이 있습니다."))
        .isEqualTo("매출이 3.5% 감소했어요. 비용 부담이 확대돼요. 불확실성이 있어요.");
  }
}
