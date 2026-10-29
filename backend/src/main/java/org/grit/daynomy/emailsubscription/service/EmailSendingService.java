package org.grit.daynomy.emailsubscription.service;

import java.time.Clock;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.external.ResendEmailClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@ConditionalOnProperty(name = "email.delivery.enabled", havingValue = "true")
public class EmailSendingService {
  private final EmailDeliveryService deliveries;
  private final ResendEmailClient client;
  private final Clock clock;

  @Autowired
  public EmailSendingService(EmailDeliveryService deliveries, ResendEmailClient client) {
    this(deliveries, client, Clock.systemUTC());
  }

  EmailSendingService(EmailDeliveryService deliveries, ResendEmailClient client, Clock clock) {
    this.deliveries = deliveries;
    this.client = client;
    this.clock = clock;
  }

  // claim의 SENDING 저장이 커밋된 뒤 외부 요청을 시작해야 중복 실행과 긴 DB 잠금을 막는다.
  @Transactional(propagation = Propagation.NEVER)
  public void send(UUID deliveryId) {
    var claimAt = clock.instant();
    var attempt = deliveries.claim(deliveryId, claimAt);
    if (attempt.isEmpty()) return;
    var deadline =
        claimAt
            .atZone(EmailDelivery.ZONE)
            .toLocalDate()
            .atTime(10, 0)
            .atZone(EmailDelivery.ZONE)
            .toInstant();
    var beforeSend = clock.instant();
    if (!beforeSend.isBefore(deadline)) {
      deliveries.failed(deliveryId, false, beforeSend);
      return;
    }
    ResendEmailClient.Result result;
    try {
      result = client.send(attempt.get());
    } catch (RuntimeException failure) {
      deliveries.unknown(deliveryId);
      log.atWarn()
          .addKeyValue("deliveryId", deliveryId)
          .addKeyValue("errorType", failure.getClass().getSimpleName())
          .log("이메일 전송 결과 확인 필요");
      return;
    }
    switch (result.status()) {
      case ACCEPTED -> deliveries.accepted(deliveryId, result.messageId(), clock.instant());
      case RETRYABLE_FAILURE -> deliveries.failed(deliveryId, true, clock.instant());
      case PERMANENT_FAILURE -> deliveries.failed(deliveryId, false, clock.instant());
      case UNKNOWN -> deliveries.unknown(deliveryId);
    }
  }
}
