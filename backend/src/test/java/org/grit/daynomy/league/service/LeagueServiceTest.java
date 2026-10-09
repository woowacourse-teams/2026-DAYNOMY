package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorFollowRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.PortfolioTransactionRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

  @Mock private InvestorProfileRepository profileRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private SharedHoldingHistoryRepository holdingHistoryRepository;
  @Mock private PortfolioDailyReturnRepository returnRepository;
  @Mock private PortfolioTransactionRepository transactionRepository;
  @Mock private InvestmentReviewRepository reviewRepository;
  @Mock private InvestorFollowRepository followRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @InjectMocks private LeagueService service;

  @Test
  void dailyHistoryCompoundsReturnsAndResetsAtEachMondayWithoutWeekendPoints() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    SharedPortfolio portfolio = dailyProfile(week.minusWeeks(1));
    var monday = dailyReturn(portfolio, week, "10");
    var tuesday = dailyReturn(portfolio, week.plusDays(1), "-10");
    var nextMonday = dailyReturn(portfolio, week.plusWeeks(1), "2");
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(week, week.plusDays(1), week.plusWeeks(1)));
    given(
            returnRepository
                .findAllByPortfolioIdAndBaseDateBetweenAndCalculationVersionOrderByBaseDate(
                    3L, week, week.plusDays(6), 1))
        .willReturn(List.of(monday, tuesday));
    given(
            returnRepository
                .findAllByPortfolioIdAndBaseDateBetweenAndCalculationVersionOrderByBaseDate(
                    3L, week.plusWeeks(1), week.plusWeeks(1).plusDays(6), 1))
        .willReturn(List.of(nextMonday));

    var previous = service.dailyHistory("investor-1", week);
    var current = service.dailyHistory("investor-1", week.plusWeeks(1));

    assertThat(previous.days()).hasSize(2);
    assertThat(previous.days().getFirst().cumulativeReturnRate()).isEqualByComparingTo("10");
    assertThat(previous.weeklyReturnRate()).isEqualByComparingTo("-1");
    assertThat(previous.asOfDate()).isEqualTo(week.plusDays(1));
    assertThat(previous.confirmed()).isTrue();
    assertThat(current.weeklyReturnRate()).isEqualByComparingTo("2");
    assertThat(current.confirmed()).isFalse();
    assertThat(monday.getDailyReturnRate()).isEqualByComparingTo("10");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingOrExcludedDayNeverBecomesZeroOrGetsSkippedInTheCumulativeRate(boolean excluded) {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    SharedPortfolio portfolio = dailyProfile(week.minusWeeks(1));
    var monday = dailyReturn(portfolio, week, "10");
    var wednesday = dailyReturn(portfolio, week.plusDays(2), "5");
    var missing = new PortfolioDailyReturn(portfolio, week.plusDays(1));
    missing.exclude("MISSING_PRICE", Instant.now());
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(week, week.plusDays(1), week.plusDays(2)));
    given(
            returnRepository
                .findAllByPortfolioIdAndBaseDateBetweenAndCalculationVersionOrderByBaseDate(
                    3L, week, week.plusDays(6), 1))
        .willReturn(excluded ? List.of(monday, missing, wednesday) : List.of(monday, wednesday));

    var result = service.dailyHistory("investor-1", week);

    assertThat(result.days()).hasSize(3);
    assertThat(result.days().get(1).dailyReturnRate()).isNull();
    assertThat(result.days().get(1).status())
        .isEqualTo(
            excluded
                ? LeagueTypes.DailyReturnStatus.EXCLUDED
                : LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(result.days().get(2).dailyReturnRate()).isEqualByComparingTo("5");
    assertThat(result.days().get(2).cumulativeReturnRate()).isNull();
    assertThat(result.weeklyReturnRate()).isNull();
    assertThat(result.confirmed()).isFalse();
  }

  @Test
  void participationStartsNextMondayAndMissingPricesDoNotInventDates() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    dailyProfile(week);
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate()).willReturn(List.of(week));

    var result = service.dailyHistory("investor-1", week);

    assertThat(result.eligibleFrom()).isEqualTo(week.plusWeeks(1));
    assertThat(result.days().getFirst().status())
        .isEqualTo(LeagueTypes.DailyReturnStatus.NOT_PARTICIPATING);
    assertThat(result.weeklyReturnRate()).isNull();
    assertThat(result.asOfDate()).isNull();
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate()).willReturn(List.of());
    assertThat(service.dailyHistory("investor-1", week).days()).isEmpty();
  }

  @Test
  void onlyRecentEightWeeksAndPublicProfilesCanBeRead() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(mock(InvestorProfile.class)));
    for (LocalDate invalid : List.of(week.plusWeeks(1), week.minusWeeks(8))) {
      assertThatThrownBy(() -> service.dailyHistory("investor-1", invalid))
          .isInstanceOf(BusinessException.class)
          .extracting(exception -> ((BusinessException) exception).errorCode())
          .isEqualTo(LeagueErrorCode.INVALID_LEAGUE_WEEK);
    }
    assertThatThrownBy(() -> service.dailyHistory("private-investor", week))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(LeagueErrorCode.PROFILE_NOT_FOUND);
    assertThat(service.weeks().weeks()).hasSize(8);
    assertThat(service.weeks().weeks().getFirst().weekStart()).isEqualTo(week);
  }

  private SharedPortfolio dailyProfile(LocalDate enabledOn) {
    Member owner = mock(Member.class);
    given(owner.getId()).willReturn(2L);
    InvestorProfile profile =
        InvestorProfile.create(
            owner,
            "공개 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true, true, false, false, enabledOn.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(profile));
    SharedPortfolio portfolio = mock(SharedPortfolio.class);
    given(portfolio.getId()).willReturn(3L);
    given(portfolioRepository.findByMemberId(2L)).willReturn(Optional.of(portfolio));
    return portfolio;
  }

  private PortfolioDailyReturn dailyReturn(SharedPortfolio portfolio, LocalDate date, String rate) {
    var value = new PortfolioDailyReturn(portfolio, date);
    value.complete(
        new BigDecimal(rate),
        new BigDecimal("100000"),
        new BigDecimal("110000"),
        new BigDecimal("100"),
        Instant.now());
    return value;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publicDetailsUseCurrentHoldingsAndHonorHiddenAssets(boolean hidden) {
    InvestorProfile profile = mock(InvestorProfile.class);
    Member owner = mock(Member.class);
    SharedPortfolio portfolio = mock(SharedPortfolio.class);
    Asset asset = mock(Asset.class);
    SharedHoldingHistory history = mock(SharedHoldingHistory.class);
    StockDailyPrice previousPrice = mock(StockDailyPrice.class);
    StockDailyPrice currentPrice = mock(StockDailyPrice.class);
    LocalDate previousDate = LocalDate.of(2026, 9, 21);
    LocalDate currentDate = LocalDate.of(2026, 9, 22);

    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(profile));
    given(profile.isDetailPublic()).willReturn(true);
    given(profile.getMember()).willReturn(owner);
    given(owner.getId()).willReturn(2L);
    given(portfolioRepository.findByMemberId(2L)).willReturn(Optional.of(portfolio));
    given(portfolio.getId()).willReturn(3L);
    given(
            transactionRepository
                .findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
                    eq(3L), any(LocalDate.class)))
        .willReturn(List.of());
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(previousDate, currentDate));
    given(
            holdingHistoryRepository
                .findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                    eq(3L), any(Instant.class)))
        .willReturn(List.of(history));
    given(history.getAsset()).willReturn(asset);
    given(history.getChangeType()).willReturn(ChangeType.ADDED);
    given(history.getQuantity()).willReturn(10L);
    given(asset.getId()).willReturn(4L);
    if (!hidden) {
      given(asset.getName()).willReturn("삼성전자");
      given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    }
    SharedHolding visible = mock(SharedHolding.class);
    given(visible.isHidden()).willReturn(hidden);
    given(visible.getAsset()).willReturn(asset);
    given(visible.getQuantity()).willReturn(10L);
    given(holdingRepository.findAllByPortfolioIdOrderById(3L)).willReturn(List.of(visible));
    given(priceRepository.findByAssetIdAndBaseDate(4L, previousDate))
        .willReturn(Optional.of(previousPrice));
    given(priceRepository.findByAssetIdAndBaseDate(4L, currentDate))
        .willReturn(Optional.of(currentPrice));
    given(previousPrice.getClosePrice()).willReturn(new BigDecimal("100"));
    given(currentPrice.getClosePrice()).willReturn(new BigDecimal("110"));

    var response = service.investorDetail("investor-1");

    assertThat(response.asOfDate()).isEqualTo(currentDate);
    if (hidden) {
      assertThat(response.holdings()).isEmpty();
    } else {
      assertThat(response.holdings())
          .singleElement()
          .satisfies(
              holding -> {
                assertThat(holding.assetName()).isEqualTo("삼성전자");
                assertThat(holding.weight()).isEqualByComparingTo("100.00");
                assertThat(holding.weeklyContributionRate()).isEqualByComparingTo("10.00");
              });
    }
    then(transactionRepository)
        .should()
        .findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
            eq(3L), any(LocalDate.class));
  }
}
