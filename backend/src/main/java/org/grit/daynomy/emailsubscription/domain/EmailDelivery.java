package org.grit.daynomy.emailsubscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.member.domain.Member;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "email_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailDelivery {
  public static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Seoul");

  public enum Status {
    READY,
    SENDING,
    ACCEPTED,
    RETRY_PENDING,
    UNKNOWN,
    FAILED,
    CANCELLED,
    EXPIRED
  }

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "delivery_date", nullable = false)
  private LocalDate deliveryDate;

  @Column(name = "as_of", nullable = false)
  private Instant asOf;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false)
  private String body;

  @Column(name = "html_body")
  private String htmlBody;

  @Column(name = "unsubscribe_url")
  private String unsubscribeUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "stock_digest", columnDefinition = "jsonb")
  private EmailStockDigest stockDigest;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "supplement_digest", columnDefinition = "jsonb")
  private EmailDigestSupplement supplementDigest;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "analysis_ids", nullable = false, columnDefinition = "jsonb")
  private List<Long> analysisIds;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "provider_message_id")
  private String providerMessageId;

  public EmailDelivery(Member member, Instant asOf, EmailStockDigest digest, LocalDate date) {
    this(member, asOf, digest, EmailDigestSupplement.empty(), date);
  }

  public EmailDelivery(
      Member member,
      Instant asOf,
      EmailStockDigest digest,
      EmailDigestSupplement supplement,
      LocalDate date) {
    this.member = Objects.requireNonNull(member);
    this.asOf = Objects.requireNonNull(asOf);
    if (!asOf.atZone(ZONE).toLocalDate().equals(date)
        || digest == null
        || !digest.currentNotification()
        || (!digest.hasNewInformation() && !supplement.shouldSend()))
      throw new IllegalArgumentException("당일의 유효한 종목 메일만 준비할 수 있습니다.");
    this.id = UUID.randomUUID();
    this.deliveryDate = date;
    this.subject = "[DAYNOMY] 내 자산 변화와 새 소식";
    this.body = supplement.body() + digest.emailSections("");
    this.stockDigest = digest;
    this.supplementDigest = supplement;
    this.analysisIds = List.of();
    this.status = Status.READY;
    this.nextAttemptAt = at(9, 0);
  }

  public void completeTemplate(String nickname, String frontendBaseUrl) {
    if (status != Status.READY || attempts != 0 || unsubscribeUrl == null || !currentNotification())
      throw new IllegalStateException("해지 링크가 준비된 미발송 메일만 구성할 수 있습니다.");
    var uri = java.net.URI.create(frontendBaseUrl);
    boolean local =
        "http".equals(uri.getScheme())
            && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
    if (uri.getHost() == null
        || !(local || "https".equals(uri.getScheme()))
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
      throw new IllegalArgumentException("프론트엔드 주소는 HTTPS 또는 로컬 원점이어야 합니다.");
    var base =
        frontendBaseUrl.endsWith("/")
            ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
            : frontendBaseUrl;
    var name = nickname == null || nickname.isBlank() ? "회원" : nickname.replaceAll("[\\r\\n]", " ");
    body =
        "안녕하세요, "
            + name
            + "님.\n\n"
            + supplementDigest.body()
            + (supplementDigest.analysis() == null
                ? ""
                : supplementDigest.analysis().body() + "업데이트된 분석 보기: " + base + "/\n\n")
            + stockDigest.emailSections(base)
            + (supplementDigest.analysis() == null
                ? "자세한 자산 현황은 포트폴리오에서 확인해보세요.\n\n내 포트폴리오 확인하기: " + base + "/\n"
                : "")
            + "\nDAYNOMY\n알림 설정: "
            + base
            + "/mypage"
            + "\n수신 해지: "
            + unsubscribeUrl;
    htmlBody =
        EmailHtmlTemplate.render(
            name, deliveryDate, stockDigest, supplementDigest, base, unsubscribeUrl);
  }

  public boolean currentNotification() {
    return stockDigest != null && stockDigest.currentNotification() && analysisIds.isEmpty();
  }

  public boolean claim(Instant now) {
    if (status != Status.READY && status != Status.RETRY_PENDING) return false;
    if (!now.isBefore(at(10, 0))) {
      status = Status.EXPIRED;
      return false;
    }
    if (now.isBefore(nextAttemptAt)) return false;
    status = Status.SENDING;
    attempts++;
    lastAttemptAt = now;
    return true;
  }

  public void attachUnsubscribeLink(String url) {
    if (status != Status.READY || attempts != 0 || unsubscribeUrl != null)
      throw new IllegalStateException("준비 중인 메일에만 해지 링크를 한 번 설정할 수 있습니다.");
    if (url == null || url.isBlank()) throw new IllegalArgumentException("해지 링크가 필요합니다.");
    unsubscribeUrl = url;
  }

  /** 제공자의 전송 접수 확인이며 수신함 도착을 의미하지 않는다. */
  public void accepted(String messageId, Instant at) {
    if (messageId == null || messageId.isBlank() || messageId.length() > 255)
      throw new IllegalArgumentException("제공자 메시지 ID가 필요합니다.");
    if (status == Status.ACCEPTED && messageId.equals(providerMessageId)) return;
    if (status != Status.SENDING && status != Status.UNKNOWN)
      throw new IllegalStateException("전송 중이거나 확인 대기 중인 메일만 접수 처리할 수 있습니다.");
    if (at.isBefore(lastAttemptAt)) throw new IllegalArgumentException("전송 시도 이전의 접수 시각입니다.");
    providerMessageId = messageId;
    acceptedAt = at;
    status = Status.ACCEPTED;
  }

  /** 제공자가 접수하지 않았다고 확인된 실패에만 사용한다. */
  public void failed(boolean retryable, Instant now) {
    requireSending();
    if (now.isBefore(lastAttemptAt)) throw new IllegalArgumentException("전송 시도 이전의 실패 시각입니다.");
    if (!retryable || attempts >= 3 || !now.isBefore(at(10, 0))) {
      status = Status.FAILED;
      return;
    }
    Instant next = attempts == 1 && now.isBefore(at(9, 10)) ? at(9, 10) : at(9, 30);
    if (!next.isAfter(now)) {
      status = Status.FAILED;
      return;
    }
    nextAttemptAt = next;
    status = Status.RETRY_PENDING;
  }

  public void unknown() {
    requireSending();
    status = Status.UNKNOWN;
  }

  public void cancel() {
    if (status == Status.READY || status == Status.RETRY_PENDING) status = Status.CANCELLED;
  }

  private void requireSending() {
    if (status != Status.SENDING) throw new IllegalStateException("진행 중인 전송만 결과를 기록할 수 있습니다.");
  }

  private Instant at(int hour, int minute) {
    return deliveryDate.atTime(hour, minute).atZone(ZONE).toInstant();
  }
}
