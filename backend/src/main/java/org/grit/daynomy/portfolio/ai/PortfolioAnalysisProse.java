package org.grit.daynomy.portfolio.ai;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 생성된 설명만 검사한다. 종목명, 출처 제목, URL에는 적용하지 않는다. */
final class PortfolioAnalysisProse {
  private PortfolioAnalysisProse() {}

  static String normalize(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("분석 설명이 비어 있습니다.");
    }
    List<String> paragraphs = new ArrayList<>();
    for (String paragraph : text.strip().split("\\R\\s*\\R")) {
      String value = paragraph.strip();
      if (value.endsWith("요")) {
        value += ".";
      }
      BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.KOREAN);
      sentences.setText(value);
      int start = sentences.first();
      for (int end = sentences.next();
          end != BreakIterator.DONE;
          start = end, end = sentences.next()) {
        String sentence = value.substring(start, end).strip();
        if (!sentence.endsWith("요.")) {
          throw new IllegalArgumentException("분석 설명은 해요체와 온점으로 끝나야 합니다.");
        }
      }
      paragraphs.add(value);
    }
    return String.join("\n\n", paragraphs);
  }
}
