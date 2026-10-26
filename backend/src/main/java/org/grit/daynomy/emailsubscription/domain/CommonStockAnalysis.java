package org.grit.daynomy.emailsubscription.domain;

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
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourcePolicy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "email_common_stock_analyses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonStockAnalysis extends BaseEntity {

  public enum Status {
    PENDING,
    VERIFIED,
    REJECTED,
    INVALIDATED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(name = "asset_code", nullable = false, length = 50)
  private String assetCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StockMarket market;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AssetCategory category;

  @Column(nullable = false, length = 100)
  private String model;

  @Column(name = "analysis_version", nullable = false, length = 100)
  private String analysisVersion;

  @Column(name = "evidence_version", nullable = false, length = 100)
  private String evidenceVersion;

  @Column(name = "as_of", nullable = false)
  private Instant asOf;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private CommonStockAnalysisContent content;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "analyzed_at", nullable = false)
  private Instant analyzedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private CommonStockAnalysisVerification verification;

  @Column(name = "invalidated_at")
  private Instant invalidatedAt;

  @Column(name = "invalidation_reason", length = 500)
  private String invalidationReason;

  public CommonStockAnalysis(
      Asset asset,
      CommonStockAnalysisKey key,
      CommonStockAnalysisContent content,
      String contentHash,
      Instant analyzedAt) {
    if (!Objects.equals(asset.getId(), key.assetId())
        || asset.getAssetCode() == null
        || asset.getMarket() == null
        || (asset.getCategory() != AssetCategory.STOCK
            && asset.getCategory() != AssetCategory.ETF)) {
      throw new IllegalArgumentException("종목 코드와 시장이 확인된 동일 자산만 분석할 수 있습니다.");
    }
    this.analyzedAt = Objects.requireNonNull(analyzedAt).truncatedTo(ChronoUnit.MICROS);
    if (key.asOf().isAfter(this.analyzedAt)) {
      throw new IllegalArgumentException("분석 기준 시점은 분석 완료 시점보다 늦을 수 없습니다.");
    }
    this.asset = asset;
    this.assetCode = asset.getAssetCode();
    this.market = asset.getMarket();
    this.category = asset.getCategory();
    this.model = key.model();
    this.analysisVersion = key.analysisVersion();
    this.evidenceVersion = key.evidenceVersion();
    this.asOf = key.asOf();
    this.content = Objects.requireNonNull(content);
    if (contentHash == null || !contentHash.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("분석 내용의 해시가 필요합니다.");
    }
    this.contentHash = contentHash;
    this.status = Status.PENDING;
  }

  public void recordVerification(CommonStockAnalysisVerification result) {
    if (!contentHash.equals(result.analysisContentHash())) {
      throw new IllegalArgumentException("저장된 분석 내용과 검증 대상이 다릅니다.");
    }
    if (result.equals(verification)) {
      return;
    }
    if (status != Status.PENDING) {
      throw new IllegalStateException("확정된 검증 결과는 덮어쓸 수 없습니다.");
    }
    if (result.checkedAt().isBefore(analyzedAt)) {
      throw new IllegalArgumentException("분석 완료 이전의 검증 결과는 사용할 수 없습니다.");
    }
    verification = result;
    status = sufficientEvidence(result) ? Status.VERIFIED : Status.REJECTED;
  }

  public void invalidate(String reason, Instant at) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new IllegalArgumentException("무효화 사유는 1~500자로 입력해야 합니다.");
    Objects.requireNonNull(at);
    if (at.isBefore(analyzedAt)) throw new IllegalArgumentException("분석 완료 이전에 무효화할 수 없습니다.");
    if (status == Status.INVALIDATED) return;
    status = Status.INVALIDATED;
    invalidatedAt = at.truncatedTo(ChronoUnit.MICROS);
    invalidationReason = reason.strip();
  }

  private boolean sufficientEvidence(CommonStockAnalysisVerification result) {
    if (content.direction() == null
        || content.impactLevel() == null
        || content.issueSummary() == null
        || content.issueSummary().isBlank()
        || content.reason() == null
        || content.reason().isBlank()
        || content.sources().isEmpty()) {
      return false;
    }
    Set<String> evidenceUrls = new HashSet<>();
    for (Source source : content.sources()) {
      if (!usableSource(source) || !evidenceUrls.add(source.url())) {
        return false;
      }
    }
    Set<String> checkedUrls = new HashSet<>();
    for (var source : result.sources()) {
      if (!source.passed() || !checkedUrls.add(source.url())) {
        return false;
      }
    }
    return evidenceUrls.equals(checkedUrls);
  }

  private boolean usableSource(Source source) {
    LocalDate cutoffDate = asOf.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
    try {
      URI uri = URI.create(source.url());
      return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null
          && uri.getUserInfo() == null
          && !PortfolioAnalysisSourcePolicy.isIndex(source.url())
          && source.factQuote() != null
          && !source.factQuote().isBlank()
          && source.excerpt() != null
          && !source.excerpt().isBlank()
          && (source.publishedOn() != null || source.occurredOn() != null)
          && (source.publishedOn() == null || !source.publishedOn().isAfter(cutoffDate))
          && (source.occurredOn() == null || !source.occurredOn().isAfter(cutoffDate));
    } catch (IllegalArgumentException | NullPointerException exception) {
      return false;
    }
  }
}
