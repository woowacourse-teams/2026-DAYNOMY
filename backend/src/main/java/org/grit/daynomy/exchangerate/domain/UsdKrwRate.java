package org.grit.daynomy.exchangerate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "usd_krw_rates")
public class UsdKrwRate {
  @Id private LocalDate requestedDate;

  @Column(nullable = false)
  private LocalDate baseDate;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal rate;

  @Column(nullable = false)
  private Instant fetchedAt;

  public UsdKrwRate(LocalDate requestedDate, LocalDate baseDate, BigDecimal rate) {
    this(requestedDate, baseDate, rate, Instant.now());
  }

  public UsdKrwRate(
      LocalDate requestedDate, LocalDate baseDate, BigDecimal rate, Instant fetchedAt) {
    this.requestedDate = requestedDate;
    this.baseDate = baseDate;
    this.rate = rate.setScale(6, java.math.RoundingMode.HALF_UP);
    this.fetchedAt = fetchedAt;
  }
}
