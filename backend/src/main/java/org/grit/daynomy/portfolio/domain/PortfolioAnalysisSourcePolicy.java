package org.grit.daynomy.portfolio.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** 개별 원문이 아닌 메인·목록 주소는 분석 근거로 사용하지 않는다. */
public final class PortfolioAnalysisSourcePolicy {
  private PortfolioAnalysisSourcePolicy() {}

  public static String normalize(String url) {
    try {
      URI uri = URI.create(url).normalize();
      String scheme = uri.getScheme();
      String authority = uri.getRawAuthority();
      if (scheme == null || authority == null) {
        return url;
      }

      String path = uri.getRawPath();
      if (path != null && path.length() > 1 && path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      String query =
          Arrays.stream(uri.getRawQuery() == null ? new String[0] : uri.getRawQuery().split("&"))
              .filter(
                  parameter ->
                      !parameter.split("=", 2)[0].toLowerCase(Locale.ROOT).startsWith("utm_"))
              .collect(Collectors.joining("&"));

      return scheme.toLowerCase(Locale.ROOT)
          + "://"
          + authority.toLowerCase(Locale.ROOT)
          + (path == null ? "" : path)
          + (query.isBlank() ? "" : "?" + query);
    } catch (IllegalArgumentException exception) {
      return url;
    }
  }

  public static boolean isIndex(String url) {
    URI uri = URI.create(url);
    String path =
        uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
    String host = uri.getHost();
    if (host != null && (host.equals("wikipedia.org") || host.endsWith(".wikipedia.org"))) {
      return true;
    }
    if (path.isEmpty()) return true;
    String query = uri.getQuery();
    if (query != null
        && Arrays.stream(query.split("&"))
            .anyMatch(part -> part.matches("(?i)(id|articleId|newsId|seq|no|idx|rcpNo)=[^&]+"))) {
      return false;
    }
    path = path.replaceAll("/(en|eng|ko|kr|kor|ja|jp|zh|en-us|en-gb|ko-kr)$", "");
    return path.isEmpty()
        || path.matches(".*/ir/(reference|references)(\\.(html?|aspx?|jsp|do))?")
        || path.matches(
            ".*/(index|home|default|main|list|ir[0-9]*_notices)(\\.(html?|aspx?|jsp|do))?")
        || path.matches(
            ".*/(all|news|newsroom|investor|investors|investor-relations|events|notices|official_notice|iractivity|category|categories|tag|tags|ir|reports|public-disclosure|disclosure|press-releases|press-release|pressreleases|earnings|earnings-release|earnings-releases|earningsrelease|earningsannouncement|irupdates|irnews|ui-fr-ir01|ui-fr-ir06|financial-summary|facts-figures|press-room)")
        || path.contains("/tag/")
        || path.contains("/category/");
  }
}
