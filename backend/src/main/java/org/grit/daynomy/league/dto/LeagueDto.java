package org.grit.daynomy.league.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.DailyReturnStatus;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.HoldingPeriod;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.PortfolioTransaction;

public final class LeagueDto {
  private LeagueDto() {}

  public record ProfileRequest(
      @NotBlank @Size(max = 20) String displayName,
      @NotNull ExperienceLevel experienceLevel,
      @NotNull RiskProfile riskProfile,
      @NotNull @Size(max = 120) String bio) {}

  public record PublicationRequest(
      boolean profilePublic,
      boolean leagueEnabled,
      boolean allocationPublic,
      @JsonAlias("premiumDetailEnabled") boolean detailPublic) {}

  public record ProfileResponse(
      String publicId,
      String displayName,
      String bio,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile,
      boolean profilePublic,
      boolean leagueEnabled,
      boolean allocationPublic,
      boolean detailPublic,
      Instant leagueEnabledAt) {
    public static ProfileResponse from(InvestorProfile profile) {
      return new ProfileResponse(
          profile.getPublicId(),
          profile.getDisplayName(),
          profile.getBio(),
          profile.getExperienceLevel(),
          profile.getRiskProfile(),
          profile.isProfilePublic(),
          profile.isLeagueEnabled(),
          profile.isAllocationPublic(),
          profile.isDetailPublic(),
          profile.getLeagueEnabledAt());
    }
  }

  public record DecisionRequest(
      @NotBlank @Size(max = 500) String reason,
      @NotNull HoldingPeriod expectedHoldingPeriod,
      @NotBlank @Size(max = 300) String expectedChange,
      @NotBlank @Size(max = 300) String invalidationCondition,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal maximumAcceptableLossRate) {}

  public record DecisionRecordRequest(
      @NotBlank @Size(max = 80) String requestKey,
      @NotNull @Min(1) Long assetId,
      @NotNull @Valid DecisionRequest decision) {}

  public record ReviewRequest(
      @NotBlank @Size(max = 500) String actualResult,
      @NotBlank @Size(max = 500) String differenceFromExpectation,
      @NotBlank @Size(max = 500) String nextAction) {}

  public record ReviewResponse(
      Long id,
      String actualResult,
      String differenceFromExpectation,
      String nextAction,
      Instant createdAt) {
    public static ReviewResponse from(InvestmentReview review) {
      return new ReviewResponse(
          review.getId(),
          review.getActualResult(),
          review.getDifferenceFromExpectation(),
          review.getNextAction(),
          review.getCreatedAt());
    }
  }

  public record TransactionResponse(
      Long id,
      Long assetId,
      String assetCode,
      String assetName,
      String category,
      TransactionType transactionType,
      long quantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      LocalDate tradedOn,
      String reason,
      HoldingPeriod expectedHoldingPeriod,
      String expectedChange,
      String invalidationCondition,
      BigDecimal maximumAcceptableLossRate,
      boolean writtenAfterTrade,
      List<ReviewResponse> reviews) {
    public static TransactionResponse from(
        PortfolioTransaction transaction, List<InvestmentReview> reviews) {
      return new TransactionResponse(
          transaction.getId(),
          transaction.getAsset().getId(),
          transaction.getAsset().getAssetCode(),
          transaction.getAsset().getName(),
          transaction.getAsset().getCategory().name(),
          transaction.getTransactionType(),
          transaction.getQuantity(),
          transaction.getUnitPrice(),
          transaction.getFee(),
          transaction.getTradedOn(),
          transaction.getDecisionReason(),
          transaction.getExpectedHoldingPeriod(),
          transaction.getExpectedChange(),
          transaction.getInvalidationCondition(),
          transaction.getMaximumAcceptableLossRate(),
          transaction.isWrittenAfterTrade(),
          reviews.stream().map(ReviewResponse::from).toList());
    }
  }

  public record TransactionListResponse(List<TransactionResponse> transactions) {}

  public record WeekResponse(LocalDate weekStart, LocalDate weekEnd, boolean confirmed) {}

  public record WeeksResponse(List<WeekResponse> weeks) {}

  public record RankingEntryResponse(
      int rank,
      String publicId,
      String displayName,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile,
      BigDecimal weeklyReturnRate,
      BigDecimal eightWeekReturnRate,
      BigDecimal maxDrawdownRate,
      BigDecimal volatilityRate,
      BigDecimal maxHoldingWeight,
      int decisionCount,
      BigDecimal reviewCompletionRate,
      boolean followed) {}

  public record RankingResponse(
      LocalDate weekStart,
      LocalDate weekEnd,
      LeagueType leagueType,
      boolean confirmed,
      int totalCount,
      List<RankingEntryResponse> rankings,
      LocalDate asOfDate) {}

  public record AllocationResponse(BigDecimal stockWeight, BigDecimal etfWeight) {}

  public record PerformanceResponse(
      BigDecimal weeklyReturnRate,
      BigDecimal eightWeekReturnRate,
      BigDecimal maxDrawdownRate,
      BigDecimal volatilityRate,
      BigDecimal maxHoldingWeight) {}

  public record HistoryPointResponse(
      LocalDate weekStart, BigDecimal weeklyReturnRate, BigDecimal maxDrawdownRate) {}

  public record DailyReturnPointResponse(
      LocalDate baseDate,
      BigDecimal dailyReturnRate,
      BigDecimal cumulativeReturnRate,
      DailyReturnStatus status,
      String reason) {}

  public record DailyHistoryResponse(
      LocalDate weekStart,
      LocalDate weekEnd,
      LocalDate asOfDate,
      LocalDate eligibleFrom,
      boolean confirmed,
      BigDecimal weeklyReturnRate,
      List<DailyReturnPointResponse> days) {}

  public record PublicInvestorResponse(
      String publicId,
      String displayName,
      String bio,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile,
      PerformanceResponse performance,
      AllocationResponse allocation,
      List<HistoryPointResponse> history,
      int decisionCount,
      BigDecimal reviewCompletionRate,
      boolean followed,
      boolean detailAvailable) {}

  public record PublicDecisionResponse(
      Long transactionId,
      String assetName,
      String category,
      TransactionType transactionType,
      LocalDate tradedOn,
      String reason,
      HoldingPeriod expectedHoldingPeriod,
      String expectedChange,
      String invalidationCondition,
      BigDecimal maximumAcceptableLossRate,
      boolean writtenAfterTrade,
      List<ReviewResponse> reviews) {}

  public record PublicHoldingResponse(
      String assetName,
      String category,
      BigDecimal weight,
      BigDecimal weeklyContributionRate,
      String reason) {}

  public record InvestorDetailResponse(
      String publicId,
      LocalDate asOfDate,
      List<PublicHoldingResponse> holdings,
      List<PublicDecisionResponse> decisions) {}

  public record FollowSummaryResponse(
      String publicId,
      String displayName,
      Integer rank,
      BigDecimal weeklyReturnRate,
      int newReviewCount) {}

  public record FollowSummaryListResponse(List<FollowSummaryResponse> investors) {}
}
