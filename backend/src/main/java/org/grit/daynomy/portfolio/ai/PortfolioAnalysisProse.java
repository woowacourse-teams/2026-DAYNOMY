package org.grit.daynomy.portfolio.ai;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 생성된 설명만 검사한다. 종목명, 출처 제목, URL에는 적용하지 않는다. */
public final class PortfolioAnalysisProse {
  private PortfolioAnalysisProse() {}

  public static void requireSupportedDates(String text, String evidence) {
    var dates = java.util.regex.Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}").matcher(text);
    while (dates.find()) {
      if (!evidence.contains(dates.group())) {
        throw new IllegalArgumentException("사건 날짜를 연결된 근거에서 확인할 수 없습니다.");
      }
    }
  }

  public static String normalize(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("분석 설명이 비어 있습니다.");
    }
    List<String> paragraphs = new ArrayList<>();
    for (String paragraph : text.strip().split("\\R\\s*\\R")) {
      String value = paragraph.strip();
      if (value.endsWith("요")) {
        value += ".";
      }
      if (value.contains("**")
          || value.contains("…")
          || value.contains("..")
          || value.contains(";")
          || value.contains("http://")
          || value.contains("https://")) {
        throw new IllegalArgumentException("분석 설명에는 말줄임표·세미콜론·URL을 사용할 수 없습니다.");
      }
      BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.KOREAN);
      sentences.setText(value);
      int start = sentences.first();
      for (int end = sentences.next();
          end != BreakIterator.DONE;
          start = end, end = sentences.next()) {
        String sentence = value.substring(start, end).strip();
        if (!sentence.endsWith("요.")
            || !sentence.matches("(?s).*[가-힣]{2,}.*")
            || containsEnglishProse(sentence)) {
          throw new IllegalArgumentException("분석 설명은 해요체와 온점으로 끝나야 합니다.");
        }
      }
      paragraphs.add(value);
    }
    return String.join("\n\n", paragraphs);
  }

  private static boolean containsEnglishProse(String sentence) {
    var phrases =
        java.util.regex.Pattern.compile("[A-Za-z]+(?:[ \t]+[A-Za-z]+){4,}").matcher(sentence);
    while (phrases.find()) {
      // 영문 고유명사 뒤에 한국어 조사가 이어지는 경우는 한국어 설명의 일부다.
      boolean properName =
          Arrays.stream(phrases.group().split("[ \t]+"))
              .allMatch(
                  word ->
                      Character.isUpperCase(word.charAt(0))
                          || Set.of("of", "the", "and", "for", "in", "on", "to").contains(word));
      boolean followedByParticle =
          sentence.substring(phrases.end()).matches("(?s)^(은|는|이|가|을|를|의|와|과|에|로|으로).*");
      if (!properName || !followedByParticle) return true;
    }
    return false;
  }
}
