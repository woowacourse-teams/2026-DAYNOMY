package org.grit.daynomy.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposal;
import org.grit.daynomy.portfolio.domain.PortfolioReviewProposalSnapshot;

public final class PortfolioReviewProposalDto {

  private PortfolioReviewProposalDto() {}

  public record Request(
      @NotEmpty(message = "검토안 자산은 하나 이상 필요합니다.")
          @Size(max = 50, message = "검토안 자산은 최대 50개까지 등록할 수 있습니다.")
          List<@NotNull @Valid AssetRequest> assets) {

    public Request {
      assets = assets == null ? null : List.copyOf(assets);
    }
  }

  public record AssetRequest(
      @NotNull @Positive Long assetId,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("100")
          @Digits(integer = 3, fraction = 2)
          BigDecimal weight) {}

  public record Response(
      Long id,
      Long shareId,
      Long commentId,
      List<AssetResponse> assets,
      Instant createdAt,
      Instant updatedAt) {

    public Response {
      assets = List.copyOf(assets);
    }

    public static Response from(PortfolioReviewProposal proposal) {
      return new Response(
          proposal.getId(),
          proposal.getShare().getId(),
          proposal.getComment().getId(),
          proposal.getSnapshot().assets().stream().map(AssetResponse::from).toList(),
          proposal.getCreatedAt(),
          proposal.getUpdatedAt());
    }
  }

  public record AssetResponse(
      Long assetId, String assetName, AssetCategory category, BigDecimal weight) {

    private static AssetResponse from(PortfolioReviewProposalSnapshot.Asset asset) {
      return new AssetResponse(
          asset.assetId(), asset.assetName(), asset.category(), asset.weight());
    }
  }
}
