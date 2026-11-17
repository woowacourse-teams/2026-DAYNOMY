package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;

public final class PortfolioAnalysisShareDto {

  private PortfolioAnalysisShareDto() {}

  /** 작성자 전용 임시 구성의 기준 평가금액(KRW). 공개 공유 응답에는 포함하지 않는다. */
  public record SimulationAssetResponse(
      Long assetId, String assetName, AssetCategory category, BigDecimal amount) {}

  public record CreateRequest(
      @Positive Long analysisId,
      @NotBlank @Size(max = 200) @Pattern(regexp = "^[^<>]*$", message = "HTML 형식은 입력할 수 없습니다.")
          String question,
      @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 255) String> assetNames) {}

  public record CommentRequest(String content) {
    public CommentRequest {
      content = PortfolioAnalysisComment.normalizeContent(content);
    }
  }

  public record CreatedResponse(OwnerResponse share, String token) {}

  public record ExplanationRequest(
      @NotNull @Size(max = 2000) @Pattern(regexp = "^[^<>]*$", message = "HTML 형식은 입력할 수 없습니다.")
          String content) {}

  public record OwnerResponse(
      Long id,
      Long analysisId,
      String question,
      String additionalExplanation,
      Instant additionalExplanationUpdatedAt,
      PortfolioAnalysisShare.Status status,
      Instant expiresAt,
      Instant createdAt,
      boolean allAssetsShared,
      List<AssetResponse> sharedAssets,
      List<CommentResponse> comments) {

    public OwnerResponse {
      sharedAssets = List.copyOf(sharedAssets);
      comments = List.copyOf(comments);
    }
  }

  public record PublicResponse(
      String ownerNickname,
      String question,
      String additionalExplanation,
      Instant additionalExplanationUpdatedAt,
      Instant expiresAt,
      SnapshotResponse analysis,
      boolean canComment,
      List<CommentResponse> myComments,
      OwnerResponse ownerShare) {

    public PublicResponse {
      myComments = List.copyOf(myComments);
    }
  }

  public record CommentResponse(
      Long id,
      String authorNickname,
      String content,
      Instant createdAt,
      Instant updatedAt,
      List<ReplyResponse> replies) {

    public CommentResponse {
      replies = List.copyOf(replies);
    }
  }

  public record ReplyResponse(
      Long id,
      String authorNickname,
      String content,
      boolean fromOwner,
      boolean editable,
      Instant createdAt,
      Instant updatedAt) {}

  public record SnapshotResponse(
      int totalAssetCount,
      int analyzedAssetCount,
      boolean allAssetsShared,
      boolean hasAnalysis,
      ImpactDirection overallDirection,
      BigDecimal overallScore,
      BigDecimal positiveImpactScore,
      BigDecimal negativeImpactScore,
      Instant analyzedAt,
      String overallImpact,
      List<AssetImpactResponse> impacts,
      List<SourceResponse> sources,
      List<AssetResponse> excludedAssets) {

    public SnapshotResponse {
      impacts = List.copyOf(impacts);
      sources = List.copyOf(sources);
      excludedAssets = List.copyOf(excludedAssets);
    }

    public static SnapshotResponse from(PortfolioAnalysisPublicSnapshot snapshot) {
      return new SnapshotResponse(
          snapshot.totalAssetCount(),
          snapshot.analyzedAssetCount(),
          snapshot.allAssetsShared(),
          snapshot.hasAnalysis(),
          snapshot.overallDirection(),
          snapshot.overallScore(),
          snapshot.positiveImpactScore(),
          snapshot.negativeImpactScore(),
          snapshot.analyzedAt(),
          snapshot.overallImpact(),
          snapshot.impacts().stream().map(AssetImpactResponse::from).toList(),
          snapshot.sources().stream().map(SourceResponse::from).toList(),
          snapshot.excludedAssets().stream().map(AssetResponse::from).toList());
    }
  }

  public record AssetResponse(String assetName, BigDecimal weight) {
    static AssetResponse from(PortfolioAnalysisPublicSnapshot.Asset asset) {
      return new AssetResponse(asset.assetName(), asset.weight());
    }
  }

  public record AssetImpactResponse(
      String assetName,
      BigDecimal weight,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      String evidenceSentence,
      List<SourceResponse> sources,
      HoldingContextResponse holding,
      int rank) {

    public AssetImpactResponse {
      sources = List.copyOf(sources);
    }

    static AssetImpactResponse from(PortfolioAnalysisPublicSnapshot.AssetImpact impact) {
      return new AssetImpactResponse(
          impact.assetName(),
          impact.weight(),
          impact.direction(),
          impact.impactLevel(),
          impact.issueSummary(),
          impact.expectedReaction(),
          impact.outlook(),
          impact.reason(),
          impact.evidenceSentence(),
          impact.sources().stream().map(SourceResponse::from).toList(),
          HoldingContextResponse.from(impact.holding()),
          impact.rank());
    }
  }

  public record HoldingContextResponse(
      String assetCode, String market, LocalDate firstPurchasedOn, BigDecimal returnRate) {

    static HoldingContextResponse from(PortfolioAnalysisPublicSnapshot.HoldingContext holding) {
      return holding == null
          ? null
          : new HoldingContextResponse(
              holding.assetCode(),
              holding.market(),
              holding.firstPurchasedOn(),
              holding.returnRate());
    }
  }

  public record SourceResponse(
      String title,
      String url,
      LocalDate publishedOn,
      LocalDate occurredOn,
      String excerpt,
      String factQuote) {

    static SourceResponse from(PortfolioAnalysisPublicSnapshot.Source source) {
      return new SourceResponse(
          source.title(),
          source.url(),
          source.publishedOn(),
          source.occurredOn(),
          source.excerpt(),
          source.factQuote());
    }
  }
}
