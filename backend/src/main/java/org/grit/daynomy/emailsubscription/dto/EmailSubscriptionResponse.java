package org.grit.daynomy.emailsubscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;

public record EmailSubscriptionResponse(
    EmailSubscriptionStatus status,
    @Schema(description = "서버에 저장된 로그인 Google 이메일. 변경할 수 없습니다.") String email,
    @Schema(description = "구독 안내에서 선택한 적이 없는 회원인지 여부") boolean promptRequired,
    @Schema(description = "현재 또는 마지막 구독 시작 시각. 재구독 시 갱신되며 중복 구독 요청은 유지합니다.") Instant subscribedAt) {

  public static EmailSubscriptionResponse from(String email, EmailSubscription subscription) {
    EmailSubscriptionStatus status =
        subscription == null ? EmailSubscriptionStatus.UNDECIDED : subscription.getStatus();
    return new EmailSubscriptionResponse(
        status,
        email,
        status == EmailSubscriptionStatus.UNDECIDED,
        subscription == null ? null : subscription.getSubscribedAt());
  }
}
