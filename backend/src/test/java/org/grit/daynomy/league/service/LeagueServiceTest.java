package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
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
