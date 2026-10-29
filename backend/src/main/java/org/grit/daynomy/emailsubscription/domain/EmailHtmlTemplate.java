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
    if (digest != null) stocks(content, digest, baseUrl);
    paragraph(content, "필요하면 포트폴리오에서 분석을 새로 실행해보세요.");
    return """
        <!doctype html>
        <html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="light"><title>DAYNOMY · 내 자산 변화와 새 소식</title>
        <style>@media screen and (max-width:480px){.email-content{padding:28px 20px!important}.email-amount{font-size:30px!important}.email-outer{padding:16px 8px!important}}</style>
        </head><body style="margin:0;padding:0;background-color:#f1f3f7;color:#171821;font-family:Arial,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;line-height:1.7">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f1f3f7"><tr><td class="email-outer" align="center" style="padding:40px 16px">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="width:100%%;max-width:640px;background-color:#ffffff;border:1px solid #e4e7ef;border-radius:16px"><tr><td class="email-content" style="padding:40px">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border-bottom:1px solid #e4e7ef"><tr>
        <td style="padding-bottom:24px;font-size:24px;font-weight:700;letter-spacing:1px;color:#111320">DAYNOMY</td>
        <td align="right" style="padding-bottom:24px;font-size:13px;color:#52647f">%s</td></tr></table>
        <p style="margin:32px 0 8px;font-size:15px">안녕하세요, %s님.</p>
        <h1 style="margin:0 0 12px;font-size:27px;line-height:1.5;letter-spacing:-0.6px;color:#111320">내 자산 변화와 새 소식</h1>
        <p style="margin:0 0 32px;font-size:15px;color:#52647f">내 자산의 주요 변화와 새 관련 자료를 모아 전해드려요.</p>
        %s
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin-top:36px"><tr><td align="center" style="background-color:#0a0a0a;border-radius:10px">
        <a href="%s/" style="display:block;padding:16px 20px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none">내 포트폴리오 확인하기</a></td></tr></table>
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

  private static void stocks(StringBuilder html, EmailStockDigest digest, String baseUrl) {
    if (!digest.stocks().isEmpty()) {
      heading(html, "보유 종목의 가격에 변화가 있어요");
      paragraph(html, digest.baseDate() + " 종가 기준 · 현재 보유 수량 기준");
      for (var stock : digest.stocks()) {
        stockHeading(html, stock.name());
        paragraph(html, "종목 등락률 " + signed(stock.dailyReturnRate()) + "%");
        if (stock.weight() != null && stock.contribution() != null) {
          paragraph(html, digest.previousBaseDate() + " 종가 비중 " + number(stock.weight()) + "%");
          paragraph(html, "내 자산의 가격 변동 기여도 " + signed(stock.contribution()) + "%p");
        }
      }
    }
    if (!digest.contents().isEmpty()) {
      heading(html, "보유 종목에 새 관련 자료가 등록됐어요");
      for (var item : digest.contents()) {
        stockHeading(html, item.assetName());
        html.append("<p style=\"margin:8px 0;font-size:15px\">")
            .append(link(item.link(baseUrl), item.title()))
            .append("</p>");
        paragraph(
            html,
            item.platform()
                + " · 데이노미 등록일 "
                + item.registeredAt().atZone(EmailDelivery.ZONE).toLocalDate());
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
