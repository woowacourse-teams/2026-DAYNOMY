package org.grit.daynomy.portfolio.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Entity
@Table(
    name = "portfolio_transaction_imports",
    uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "fingerprint"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioTransactionImport extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "portfolio_id", nullable = false)
  private Portfolio portfolio;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  public PortfolioTransactionImport(Portfolio portfolio, String fingerprint) {
    this.portfolio = portfolio;
    this.fingerprint = fingerprint;
  }

  public record Entry(
      Long assetId,
      PortfolioTransactionType type,
      java.time.LocalDate tradeDate,
      Long quantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      BigDecimal tax,
      BigDecimal exchangeRate) {
    private String canonical() {
      return assetId
          + ":"
          + type
          + ":"
          + tradeDate
          + ":"
          + quantity
          + ":"
          + number(unitPrice)
          + ":"
          + number(fee)
          + ":"
          + number(tax)
          + ":"
          + number(exchangeRate == null ? BigDecimal.ONE : exchangeRate);
    }
  }

  public static String fingerprint(List<Entry> entries) {
    String canonical = entries.stream().map(Entry::canonical).collect(Collectors.joining("\n"));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String number(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
