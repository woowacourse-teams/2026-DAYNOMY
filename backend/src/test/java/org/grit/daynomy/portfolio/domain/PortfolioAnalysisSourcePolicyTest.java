package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisSourcePolicyTest {
  @Test
  void rejectsLanguageRootsAndAllowsIndividualDocuments() {
    for (String path :
        List.of(
            "/en/",
            "/en/pr/list.do?m=&y=2026",
            "/ir_notices.html",
            "/newsroom/en/",
            "/ko-kr/",
            "/en/newsroom/",
            "/category/earnings")) {
      assertThat(PortfolioAnalysisSourcePolicy.isIndex("https://example.com" + path)).isTrue();
    }
    for (String path :
        List.of(
            "/en/q2-2026-business-results/",
            "/news?articleId=123",
            "/dsaf001/main.do?rcpNo=20261001001")) {
      assertThat(PortfolioAnalysisSourcePolicy.isIndex("https://example.com" + path)).isFalse();
    }
  }
}
