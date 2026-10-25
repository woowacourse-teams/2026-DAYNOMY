package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisSourcePolicyTest {
  @Test
  void rejectsLanguageRootsAndAllowsIndividualDocuments() {
    for (String path :
        List.of(
            "/?rcpNo=20211222000224",
            "/en/",
            "/en/pr/list.do?m=&y=2026",
            "/ir_notices.html",
            "/investment/disclosure?page=21",
            "/newsroom/en/",
            "/ko-kr/",
            "/en/newsroom/",
            "/category/earnings",
            "/en/investment/earnings",
            "/page/presskit/press-release?t_src=mainpage",
            "/media/pressReleases?keyword=네이버배송")) {
      assertThat(PortfolioAnalysisSourcePolicy.isIndex("https://example.com" + path)).isTrue();
    }
    for (String path :
        List.of(
            "/en/q2-2026-business-results/",
            "/media/pressReleasesDetail?seq=10034577",
            "/pdf_data/20260416172457900K.pdf",
            "/news?articleId=123",
            "/dsaf001/main.do?rcpNo=20261001001")) {
      assertThat(PortfolioAnalysisSourcePolicy.isIndex("https://example.com" + path)).isFalse();
    }
  }
}
