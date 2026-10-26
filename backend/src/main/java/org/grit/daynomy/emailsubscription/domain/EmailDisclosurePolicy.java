package org.grit.daynomy.emailsubscription.domain;

import java.net.URI;

/** 등록된 공시를 분석 후보로만 분류한다. 원문 사실 검증을 대신하지 않는다. */
public final class EmailDisclosurePolicy {
  private EmailDisclosurePolicy() {}

  public static boolean isImportant(String title, String url) {
    if (title == null || url == null) return false;
    try {
      var uri = URI.create(url);
      if (!"https".equals(uri.getScheme())
          || !"dart.fss.or.kr".equals(uri.getHost())
          || uri.getUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)
          || !"/dsaf001/main.do".equals(uri.getPath())
          || uri.getQuery() == null
          || !uri.getQuery().matches("rcpNo=\\d{14}")) return false;
      var normalized = title.replaceAll("\\s+", "").replaceFirst("^\\[(기재정정|첨부정정|정정)\\]", "");
      return normalized.matches(
          "(단일판매[ㆍ·]?공급계약체결|주요사항보고서\\((유상증자결정|회사합병결정|영업양수도결정)\\)|유상증자결정|회사합병결정|주권매매거래정지)(\\([^()]*\\))*");
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
