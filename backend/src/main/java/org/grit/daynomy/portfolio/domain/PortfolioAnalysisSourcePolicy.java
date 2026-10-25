package org.grit.daynomy.portfolio.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** 개별 원문이 아닌 메인·목록 주소는 분석 근거로 사용하지 않는다. */
public final class PortfolioAnalysisSourcePolicy {
  private PortfolioAnalysisSourcePolicy() {}

  public static boolean isIndex(String url) {
    URI uri = URI.create(url);
    String path =
        uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
    String host = uri.getHost();
    if (host != null && (host.equals("wikipedia.org") || host.endsWith(".wikipedia.org"))) {
      return true;
    }
    String query = uri.getQuery();
    if (query != null
        && Arrays.stream(query.split("&"))
            .anyMatch(part -> part.matches("(?i)(id|articleId|newsId|seq|no|idx|rcpNo)=[^&]+"))) {
      return false;
    }
    path = path.replaceAll("/(en|eng|ko|kr|kor|ja|jp|zh|en-us|en-gb|ko-kr)$", "");
    return path.isEmpty()
        || path.matches(".*/(index|home|default|main|list|ir_notices)(\\.(html?|aspx?|jsp|do))?")
        || path.matches(
            ".*/(all|news|newsroom|investor|investors|events|notices|iractivity|category|categories|tag|tags|ir|reports|public-disclosure|disclosure|press-releases|press-release|pressreleases|earnings|press-room)")
        || path.contains("/tag/")
        || path.contains("/category/");
  }
}
