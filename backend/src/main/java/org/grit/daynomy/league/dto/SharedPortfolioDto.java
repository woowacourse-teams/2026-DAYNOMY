package org.grit.daynomy.league.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SharedPortfolioDto {
  private SharedPortfolioDto() {}

  public record HoldingInput(
      @NotNull @Positive Long assetId,
      @Min(1) @Max(1_000_000_000L) long quantity,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("1000000000")
          @Digits(integer = 10, fraction = 2)
          BigDecimal averagePurchasePrice) {}

  public record ImportRequest(
      @NotEmpty @Size(max = 100) List<@NotNull @Valid HoldingInput> holdings,
      boolean overwriteExisting) {}

  public record VisibilityRequest(@NotNull Boolean hidden) {}

  public record HoldingResponse(
      Long assetId,
      String assetCode,
      String assetName,
      String category,
      String market,
      long quantity,
      BigDecimal averagePurchasePrice,
      boolean hidden,
      String reason,
      LocalDate baseDate,
      BigDecimal closePrice,
      BigDecimal evaluationAmount,
      BigDecimal returnRate) {}

  public record PortfolioResponse(
      List<HoldingResponse> holdings,
      BigDecimal totalPurchaseAmount,
      BigDecimal totalEvaluationAmount,
      BigDecimal totalReturnRate,
      boolean pricesComplete) {}
}
