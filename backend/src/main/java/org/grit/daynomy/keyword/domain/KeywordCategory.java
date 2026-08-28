package org.grit.daynomy.keyword.domain;

public enum KeywordCategory {
  PERSON,
  POLICY,
  EVENT,
  TERM,
  TREND;

  public static final String DEFINITION =
      """
      PERSON(인물): 주요 인물의 최근 발언, 성향, 행보
      POLICY(정책): 정부·기관의 결정 기조와 방향
      EVENT(사건): 현재 쟁점이 된 사건의 배경과 진행
      TERM(용어): 뉴스 이해에 꼭 필요한 전문 표현
      TREND(흐름): 최근 반복되는 시장·산업 분위기
      """;
}
