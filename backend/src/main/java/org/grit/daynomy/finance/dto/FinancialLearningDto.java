package org.grit.daynomy.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.finance.domain.LearningItemType;
import org.grit.daynomy.finance.domain.LearningProgress;
import org.grit.daynomy.finance.domain.SimulatedTrade;
import org.grit.daynomy.finance.domain.SimulatedTradeType;
import org.grit.daynomy.finance.domain.WeeklyCheckIn;

public final class FinancialLearningDto {
  private FinancialLearningDto() {}

  public record ProgressUpdateRequest(
      @NotNull LearningItemType itemType, boolean completed, boolean bookmarked) {}

  public record ProgressResponse(
      String itemKey, LearningItemType itemType, boolean completed, boolean bookmarked) {
    public static ProgressResponse from(LearningProgress progress) {
      return new ProgressResponse(
          progress.getItemKey(),
          progress.getItemType(),
          progress.isCompleted(),
          progress.isBookmarked());
    }
  }

  public record CheckInRequest(
      @NotNull @DecimalMin("0") BigDecimal targetSavings,
      @NotNull @DecimalMin("0") BigDecimal targetInvestment,
      @NotNull @DecimalMin("0") BigDecimal targetDebtPayment,
      @NotNull @DecimalMin("0") BigDecimal actualSavings,
      @NotNull @DecimalMin("0") BigDecimal actualInvestment,
      @NotNull @DecimalMin("0") BigDecimal actualDebtPayment,
      @Size(max = 200) String note) {}

  public record CheckInResponse(
      LocalDate weekStart,
      BigDecimal targetSavings,
      BigDecimal targetInvestment,
      BigDecimal targetDebtPayment,
      BigDecimal actualSavings,
      BigDecimal actualInvestment,
      BigDecimal actualDebtPayment,
      String note) {
    public static CheckInResponse from(WeeklyCheckIn checkIn) {
      return new CheckInResponse(
          checkIn.getWeekStart(),
          checkIn.getTargetSavings(),
          checkIn.getTargetInvestment(),
          checkIn.getTargetDebtPayment(),
          checkIn.getActualSavings(),
          checkIn.getActualInvestment(),
          checkIn.getActualDebtPayment(),
          checkIn.getNote());
    }
  }

  public record MockTradeCreateRequest(
      @NotNull Long assetId,
      @NotNull SimulatedTradeType tradeType,
      @Min(1) long quantity,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price,
      @Size(max = 200) String reason,
      @NotNull LocalDate tradedOn) {}

  public record MockTradeResponse(
      Long id,
      Long assetId,
      String assetCode,
      String assetName,
      String category,
      String market,
      SimulatedTradeType tradeType,
      long quantity,
      BigDecimal price,
      String reason,
      LocalDate tradedOn) {
    public static MockTradeResponse from(SimulatedTrade trade) {
      return new MockTradeResponse(
          trade.getId(),
          trade.getAsset().getId(),
          trade.getAsset().getAssetCode(),
          trade.getAsset().getName(),
          trade.getAsset().getCategory().name(),
          trade.getAsset().getMarket().name(),
          trade.getTradeType(),
          trade.getQuantity(),
          trade.getPrice(),
          trade.getReason(),
          trade.getTradedOn());
    }
  }

  public record ProgressListResponse(List<ProgressResponse> progress) {}

  public record CheckInListResponse(List<CheckInResponse> checkIns) {}

  public record MockTradeListResponse(List<MockTradeResponse> trades) {}
}
