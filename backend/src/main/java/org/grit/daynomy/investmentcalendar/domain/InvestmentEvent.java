package org.grit.daynomy.investmentcalendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "investment_events",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_investment_events_source_key", columnNames = "source_key"))
public class InvestmentEvent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 30)
  private InvestmentEventType type;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "announced_at", nullable = false)
  private Instant announcedAt;

  @Column(name = "previous_value", precision = 19, scale = 4)
  private BigDecimal previousValue;

  @Column(name = "actual_value", precision = 19, scale = 4)
  private BigDecimal actualValue;

  @Column(name = "value_unit", nullable = false, length = 20)
  private String valueUnit;

  @Column(name = "source_name", nullable = false, length = 50)
  private String sourceName;

  @Column(name = "source_url", nullable = false, length = 500)
  private String sourceUrl;

  @Column(name = "source_key", nullable = false, length = 100)
  private String sourceKey;

  @Column(name = "related_asset_code", length = 50)
  private String relatedAssetCode;

  public InvestmentEvent(
      InvestmentEventType type,
      String title,
      Instant announcedAt,
      BigDecimal previousValue,
      BigDecimal actualValue,
      String valueUnit,
      String sourceName,
      String sourceUrl,
      String sourceKey,
      String relatedAssetCode) {
    this.type = Objects.requireNonNull(type);
    this.title = requireText(title, "일정 제목");
    this.announcedAt = Objects.requireNonNull(announcedAt);
    this.previousValue = previousValue;
    this.actualValue = actualValue;
    this.valueUnit = requireText(valueUnit, "값 단위");
    this.sourceName = requireText(sourceName, "출처 이름");
    this.sourceUrl = requireText(sourceUrl, "출처 URL");
    this.sourceKey = requireText(sourceKey, "출처 식별자");
    this.relatedAssetCode = relatedAssetCode;
  }

  public boolean synchronize(
      String title,
      Instant announcedAt,
      BigDecimal previousValue,
      BigDecimal actualValue,
      String valueUnit,
      String sourceName,
      String sourceUrl,
      String relatedAssetCode) {
    boolean changed =
        !Objects.equals(this.title, title)
            || !Objects.equals(this.announcedAt, announcedAt)
            || !sameNumber(this.previousValue, previousValue)
            || !sameNumber(this.actualValue, actualValue)
            || !Objects.equals(this.valueUnit, valueUnit)
            || !Objects.equals(this.sourceName, sourceName)
            || !Objects.equals(this.sourceUrl, sourceUrl)
            || !Objects.equals(this.relatedAssetCode, relatedAssetCode);
    if (!changed) {
      return false;
    }
    this.title = requireText(title, "일정 제목");
    this.announcedAt = Objects.requireNonNull(announcedAt);
    this.previousValue = previousValue;
    this.actualValue = actualValue;
    this.valueUnit = requireText(valueUnit, "값 단위");
    this.sourceName = requireText(sourceName, "출처 이름");
    this.sourceUrl = requireText(sourceUrl, "출처 URL");
    this.relatedAssetCode = relatedAssetCode;
    return true;
  }

  public InvestmentEventDirection direction() {
    if (previousValue == null || actualValue == null) {
      return InvestmentEventDirection.UNAVAILABLE;
    }
    int comparison = actualValue.compareTo(previousValue);
    if (comparison > 0) {
      return InvestmentEventDirection.INCREASED;
    }
    if (comparison < 0) {
      return InvestmentEventDirection.DECREASED;
    }
    return InvestmentEventDirection.UNCHANGED;
  }

  private boolean sameNumber(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) {
      return left == right;
    }
    return left.compareTo(right) == 0;
  }

  private String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
    }
    return value;
  }
}
