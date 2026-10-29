package org.grit.daynomy.emailsubscription.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.exception.CommonErrorCode;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailUnsubscribeService {
  public static final String PATH = "/api/email-subscriptions/unsubscribe";
  private final MemberRepository members;
  private final EmailSubscriptionRepository subscriptions;
  private final byte[] secret;
  private final String baseUrl;

  public EmailUnsubscribeService(
      MemberRepository members,
      EmailSubscriptionRepository subscriptions,
      @Value("${email.delivery.unsubscribe-secret:}") String secret,
      @Value("${email.delivery.public-base-url:}") String baseUrl,
      @Value("${email.delivery.enabled:false}") boolean enabled) {
    this.members = members;
    this.subscriptions = subscriptions;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    if (enabled) requireConfiguration();
  }

  /** 구독 시작 시각을 서명하므로 재구독 후에는 이전 메일의 링크로 해지할 수 없다. */
  public String link(Long memberId, Instant subscribedAt) {
    requireConfiguration();
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((memberId + "|" + subscribedAt).getBytes(StandardCharsets.UTF_8));
    return baseUrl
        + PATH
        + "?token="
        + payload
        + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
  }

  public void validate(String token) {
    parse(token);
  }

  public boolean matches(String url, Long memberId, Instant subscribedAt) {
    return url != null && url.equals(link(memberId, subscribedAt));
  }

  @Transactional
  public void unsubscribe(String token) {
    var target = parse(token);
    var member = members.findByIdForUpdate(target.memberId()).orElse(null);
    if (member == null || member.isWithdrawn()) return;
    var subscription = subscriptions.findById(target.memberId()).orElse(null);
    if (subscription == null) return;
    if (!target.subscribedAt().equals(subscription.getSubscribedAt())) throw invalid();
    subscription.unsubscribe();
  }

  private Target parse(String token) {
    if (secret.length < 32 || token == null || token.length() > 512) throw invalid();
    try {
      String[] parts = token.split("\\.", -1);
      if (parts.length != 2
          || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
        throw invalid();
      String[] payload =
          new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
              .split("\\|", -1);
      if (payload.length != 2) throw invalid();
      long memberId = Long.parseLong(payload[0]);
      if (memberId <= 0) throw invalid();
      return new Target(memberId, Instant.parse(payload[1]));
    } catch (IllegalArgumentException | java.time.DateTimeException malformed) {
      throw invalid();
    }
  }

  private byte[] sign(String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("이메일 해지 토큰 서명을 처리할 수 없습니다.");
    }
  }

  private void requireConfiguration() {
    try {
      var uri = URI.create(baseUrl);
      boolean local =
          "http".equals(uri.getScheme())
              && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
      if (secret.length < 32
          || uri.getHost() == null
          || !(local || "https".equals(uri.getScheme()))
          || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("32바이트 이상의 해지 서명 키와 공개 서버 주소 설정이 필요합니다.");
    }
  }

  private BusinessException invalid() {
    return new BusinessException(CommonErrorCode.INVALID_REQUEST);
  }

  private record Target(Long memberId, Instant subscribedAt) {}
}
