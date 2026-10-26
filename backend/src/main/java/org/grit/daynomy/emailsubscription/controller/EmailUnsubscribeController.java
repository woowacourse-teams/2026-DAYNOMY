package org.grit.daynomy.emailsubscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.emailsubscription.service.EmailUnsubscribeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@Tag(name = "Email Subscription")
@RestController
@RequiredArgsConstructor
public class EmailUnsubscribeController {
  private final EmailUnsubscribeService service;

  @Operation(summary = "비로그인 이메일 해지 확인", description = "GET은 구독을 변경하지 않습니다.")
  @GetMapping(value = EmailUnsubscribeService.PATH, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> confirm(@RequestParam String token) {
    service.validate(token);
    return html(
        """
        <h1>데이노미 이메일 구독 해지</h1>
        <p>이메일 알림을 그만 받으시겠어요?</p>
        <form method="post" action="%s">
          <input type="hidden" name="token" value="%s">
          <button type="submit">구독 해지</button>
        </form>
        """
            .formatted(EmailUnsubscribeService.PATH, HtmlUtils.htmlEscape(token)));
  }

  @Operation(
      summary = "토큰으로 이메일 구독 해지",
      description = "로그인·CSRF 토큰 없이 서명된 해지 토큰을 검증합니다. 같은 요청은 반복 처리할 수 있습니다.")
  @PostMapping(value = EmailUnsubscribeService.PATH, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> unsubscribe(@RequestParam String token) {
    service.unsubscribe(token);
    return html("<h1>이메일 구독 해지 요청을 처리했습니다.</h1>");
  }

  private ResponseEntity<String> html(String content) {
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .header(
            "Content-Security-Policy",
            "default-src 'none'; form-action 'self'; frame-ancestors 'none'")
        .body(
            "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>데이노미 이메일 구독</title></head><body>"
                + content
                + "</body></html>");
  }
}
