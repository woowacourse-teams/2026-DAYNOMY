package org.grit.daynomy.emailsubscription;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.member.domain.Member;

public final class EmailFixtures {
  private EmailFixtures() {}

  public static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
  public static final LocalDate MAIL_DATE = LocalDate.of(2026, 10, 26);

  public static Member member() {
    var id = UUID.randomUUID().toString();
    return Member.createGoogleMember(id, id + "@example.invalid", id.substring(0, 12), null);
  }

  public static EmailStockDigest digest(long assetId) {
    return EmailStockDigest.current(
        MAIL_DATE.minusDays(3),
        MAIL_DATE.minusDays(4),
        Map.of(assetId, 1L),
        List.of(
            new EmailStockDigest.Stock(
                assetId, "종목", new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("5"))),
        List.of());
  }

  public static EmailDelivery delivery() {
    return new EmailDelivery(member(), NOW, digest(1L), MAIL_DATE);
  }
}
