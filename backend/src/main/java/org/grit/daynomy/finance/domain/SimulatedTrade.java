package org.grit.daynomy.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "simulated_trades")
public class SimulatedTrade extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Enumerated(EnumType.STRING)
  @Column(name = "trade_type", nullable = false, length = 10)
  private SimulatedTradeType tradeType;

  @Column(nullable = false)
  private long quantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(length = 200)
  private String reason;

  @Column(name = "traded_on", nullable = false)
  private LocalDate tradedOn;

  public SimulatedTrade(
      Member member,
      Asset asset,
      SimulatedTradeType tradeType,
      long quantity,
      BigDecimal price,
      String reason,
      LocalDate tradedOn) {
    this.member = Objects.requireNonNull(member);
    this.asset = Objects.requireNonNull(asset);
    this.tradeType = Objects.requireNonNull(tradeType);
    this.quantity = quantity;
    this.price = Objects.requireNonNull(price);
    this.reason = reason == null || reason.isBlank() ? null : reason.trim();
    this.tradedOn = Objects.requireNonNull(tradedOn);
  }
}
