package org.grit.daynomy.asset.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "market_trading_days")
public class MarketTradingDay extends BaseEntity {
  @Id
  @Column(name = "base_date")
  private LocalDate baseDate;

  @Column(nullable = false)
  private boolean trading;

  @Column(name = "collected_at")
  private Instant collectedAt;

  @Column(name = "us_trading")
  private Boolean usTrading;

  @Column(name = "us_collected_at")
  private Instant usCollectedAt;

  @Column(name = "last_attempted_at")
  private Instant lastAttemptedAt;

  public MarketTradingDay(LocalDate baseDate, boolean trading) {
    this.baseDate = baseDate;
    this.trading = trading;
  }

  public Instant closesAt() {
    return baseDate.atTime(LocalTime.of(15, 30)).atZone(ZoneId.of("Asia/Seoul")).toInstant();
  }

  public boolean isLeagueTrading() {
    return trading || Boolean.TRUE.equals(usTrading);
  }

  public Instant leagueClosesAt() {
    return Boolean.TRUE.equals(usTrading) ? usClosesAt() : closesAt();
  }

  public Instant usClosesAt() {
    return baseDate.atTime(LocalTime.of(16, 0)).atZone(ZoneId.of("America/New_York")).toInstant();
  }

  public void reviseUsTrading(boolean trading) {
    if (Boolean.valueOf(trading).equals(usTrading)) return;
    usTrading = trading;
    usCollectedAt = null;
  }

  public void usCollected(Instant collectedAt) {
    if (collectedAt.isBefore(usClosesAt())) return;
    usTrading = true;
    usCollectedAt = collectedAt;
  }

  public boolean leagueCollectionAvailable() {
    return (trading && collectedAt != null)
        || (Boolean.TRUE.equals(usTrading) && usCollectedAt != null);
  }

  public void collected(Instant collectedAt) {
    if (!trading || collectedAt.isBefore(closesAt())) return;
    this.collectedAt = collectedAt;
  }

  public void attempted(Instant attemptedAt) {
    this.lastAttemptedAt = attemptedAt;
  }

  public boolean reviseTrading(boolean trading) {
    if (this.trading == trading) return false;
    this.trading = trading;
    this.collectedAt = null;
    this.lastAttemptedAt = null;
    return true;
  }
}
