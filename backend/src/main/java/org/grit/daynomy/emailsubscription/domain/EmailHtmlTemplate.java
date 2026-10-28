package org.grit.daynomy.emailsubscription.domain;

import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.web.util.HtmlUtils;

/** 준비 시점에 본문을 확정하여 재시도에도 같은 HTML을 전송한다. */
final class EmailHtmlTemplate {
  private EmailHtmlTemplate() {}

  static String render(
      String name,
      LocalDate date,
      EmailStockDigest digest,
      EmailDigestSupplement supplement,
      String baseUrl,
      String unsubscribeUrl) {
    var content = new StringBuilder();
    if (supplement.assets() != null && supplement.assets().dailyProfitLoss().signum() != 0)
      assets(content, supplement.assets());
    if (digest != null) stocks(content, digest);
    if (!supplement.events().isEmpty()) {
      heading(content, "다가오는 투자 일정이에요");
      for (var event : supplement.events()) {
        content
            .append("<h3 style=\"margin:24px 0 8px;font-size:17px;line-height:1.6\">")
            .append(text(event.title()))
            .append("</h3>");
        var announced = event.announcedAt().atZone(EmailAnalysisPolicy.ZONE);
        paragraph(
            content,
            (announced.toLocalDate().equals(date.plusDays(1)) ? "내일 · " : "")
                + announced.format(
                    DateTimeFormatter.ofPattern(
                        event.timeKnown() ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd"))
                + (event.timeKnown() ? " · 한국 시간" : " · 발표 시각 미확정"));
        source(content, event.sourceUrl());
      }
    }
    return """
        <!doctype html>
        <html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="light"><title>DAYNOMY · 내 투자 주요 변화와 일정</title>
        <style>@media screen and (max-width:480px){.email-content{padding:28px 20px!important}.email-amount{font-size:30px!important}.email-outer{padding:16px 8px!important}}</style>
        </head><body style="margin:0;padding:0;background-color:#f1f3f7;color:#171821;font-family:Arial,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;line-height:1.7">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f1f3f7"><tr><td class="email-outer" align="center" style="padding:40px 16px">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="width:100%%;max-width:640px;background-color:#ffffff;border:1px solid #e4e7ef;border-radius:16px"><tr><td class="email-content" style="padding:40px">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-bottom:1px solid #e4e7ef"><tr>
        <td style="padding-bottom:24px;font-size:24px;font-weight:700;letter-spacing:1px;color:#111320">DAYNOMY</td>
        <td align="right" style="padding-bottom:24px;font-size:13px;color:#52647f">%s</td></tr></table>
        <p style="margin:32px 0 8px;font-size:15px">안녕하세요, %s님.</p>
        <h1 style="margin:0 0 12px;font-size:27px;line-height:1.5;letter-spacing:-0.6px;color:#111320">내 투자 주요 변화와 일정</h1>
        <p style="margin:0 0 32px;font-size:15px;color:#52647f">내 포트폴리오의 주요 변화와 확인할 일정을 모아 전해드려요.</p>
        %s
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin-top:36px"><tr><td align="center" style="background-color:#0a0a0a;border-radius:10px">
        <a href="%s/" style="display:block;padding:16px 20px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none">내 포트폴리오 자세히 보기</a></td></tr></table>
        <p style="margin:32px 0 8px;font-size:14px;font-weight:700;color:#111320">DAYNOMY</p>
        <p style="margin:0;font-size:13px;color:#52647f"><a href="%s/mypage" style="color:#52647f;text-decoration:underline">알림 설정</a> · %s</p>
        </td></tr></table></td></tr></table></body></html>
        """
        .formatted(
            date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
            text(name),
            content,
            text(baseUrl),
            text(baseUrl),
            link(unsubscribeUrl, "수신 해지"));
  }

  private static void assets(StringBuilder html, EmailDigestSupplement.Assets assets) {
    heading(html, "내 자산의 변화예요");
    paragraph(html, assets.baseDate() + " 종가 기준");
    html.append("<p style=\"margin:24px 0 4px;font-size:14px;color:#52647f\">전체 평가금액</p>")
        .append(
            "<p class=\"email-amount\" style=\"margin:0;font-size:38px;font-weight:700;line-height:1.4;letter-spacing:-1px;color:#111320\">")
        .append(number(assets.evaluation()))
        .append("<span style=\"font-size:20px\">원</span></p>")
        .append(
            "<p style=\"margin:16px 0 4px;font-size:14px;color:#52647f\">해당일 보유 수량 기준 가격 변동 손익</p>")
        .append("<p style=\"margin:0;font-size:19px;font-weight:700;color:")
        .append(assets.dailyProfitLoss().signum() > 0 ? "#b42318" : "#2459b9")
        .append("\">")
        .append(signed(assets.dailyProfitLoss()))
        .append("원 · ")
        .append(signed(assets.dailyReturnRate()))
        .append("%</p>");
  }

  private static void stocks(StringBuilder html, EmailStockDigest digest) {
    boolean news = false, changed = false, prices = false;
    for (var stock : digest.stocks()) {
      if (!Boolean.TRUE.equals(stock.newFacts())) continue;
      if (!news) {
        heading(html, "보유 종목에 이런 소식이 있어요");
        paragraph(html, "오늘 주목할 보유 종목만 선별한 부분 분석입니다.");
        news = true;
      }
      stockHeading(html, stock.name());
      paragraph(html, stock.content().issueSummary());
      if (stock.previousContent() == null) paragraph(html, stock.content().reason());
      for (var source : stock.content().sources()) {
        if (source.factQuote() != null && !source.factQuote().isBlank())
          paragraph(html, source.factQuote());
        source(html, source.url());
      }
      if (stock.previousContent() == null) paragraph(html, stock.changeSummary());
    }
    for (var stock : digest.stocks()) {
      if (stock.previousContent() == null) continue;
      if (!changed) {
        heading(html, "포트폴리오 분석이 달라졌어요");
        paragraph(html, "오늘 주목할 보유 종목에 한정한 분석 변화입니다.");
        changed = true;
      }
      stockHeading(html, stock.name());
      paragraph(html, "이전 분석 근거: " + Objects.toString(stock.previousContent().reason(), ""));
      paragraph(html, "갱신된 분석: " + Objects.toString(stock.content().reason(), ""));
      paragraph(html, stock.changeSummary());
    }
    for (var stock : digest.stocks()) {
      if (stock.dailyReturnRate() == null) continue;
      if (!prices) {
        heading(html, "보유 종목의 가격 변화를 확인하세요");
        paragraph(html, digest.baseDate() + " 종가 기준 · 현재 보유 수량 기준");
        prices = true;
      }
      stockHeading(html, stock.name());
      paragraph(html, "종목 등락률 " + signed(stock.dailyReturnRate()) + "%");
      if (stock.weight() != null && stock.contribution() != null) {
        paragraph(html, digest.previousBaseDate() + " 종가 비중 " + number(stock.weight()) + "%");
        paragraph(html, "내 자산의 가격 변동 기여도 " + signed(stock.contribution()) + "%p");
      }
    }
  }

  private static void heading(StringBuilder html, String title) {
    html.append(
            "<h2 style=\"margin:36px 0 12px;padding-top:28px;border-top:1px solid #e4e7ef;font-size:20px;line-height:1.5;letter-spacing:-0.4px;color:#111320\">")
        .append(text(title))
        .append("</h2>");
  }

  private static void stockHeading(StringBuilder html, String name) {
    html.append("<h3 style=\"margin:24px 0 8px;font-size:17px;color:#111320\">")
        .append(text(name))
        .append("</h3>");
  }

  private static void paragraph(StringBuilder html, String value) {
    if (value != null && !value.isBlank())
      html.append("<p style=\"margin:8px 0;font-size:15px;overflow-wrap:anywhere\">")
          .append(text(value))
          .append("</p>");
  }

  private static void source(StringBuilder html, String url) {
    if (url != null && !url.isBlank())
      html.append("<p style=\"margin:8px 0;font-size:13px;overflow-wrap:anywhere\">출처: ")
          .append(link(url, url))
          .append("</p>");
  }

  private static String link(String url, String label) {
    try {
      var uri = URI.create(url);
      boolean local =
          "http".equals(uri.getScheme())
              && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
      if (uri.getHost() != null
          && uri.getUserInfo() == null
          && ("https".equals(uri.getScheme()) || local))
        return "<a href=\""
            + text(url)
            + "\" style=\"color:#2459b9;text-decoration:underline;overflow-wrap:anywhere\">"
            + text(label)
            + "</a>";
    } catch (IllegalArgumentException | NullPointerException invalid) {
      // 검증되지 않은 주소는 클릭 가능한 링크로 만들지 않는다.
    }
    return text(label);
  }

  private static String text(String value) {
    return HtmlUtils.htmlEscape(Objects.toString(value, ""))
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "<br>");
  }

  private static String number(BigDecimal value) {
    var format = NumberFormat.getNumberInstance(Locale.KOREA);
    format.setMaximumFractionDigits(2);
    return format.format(value);
  }

  private static String signed(BigDecimal value) {
    return (value.signum() > 0 ? "+" : "") + number(value);
  }
}
