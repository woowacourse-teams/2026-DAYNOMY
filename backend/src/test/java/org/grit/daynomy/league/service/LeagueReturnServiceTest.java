package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.league.domain.*;
import org.grit.daynomy.league.repository.*;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueReturnServiceTest {
  @Mock private InvestorProfileRepository profileRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingHistoryRepository historyRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @Mock private PortfolioDailyReturnRepository returnRepository;
  @InjectMocks private LeagueReturnService service;

  @Test
  void hiddenSharedHoldingIsIncludedAndOnlyStateBeforeTheDayIsUsed() {
    var profile = mock(InvestorProfile.class);
    var member = mock(Member.class);
    var portfolio = mock(SharedPortfolio.class);
    var asset =
        Asset.listedStock(
            "종목", "005930", StockMarket.KOSPI, "KR7005930003", LocalDate.of(2026, 9, 1));
    var holding = new SharedHolding(portfolio, asset, 2, new BigDecimal("70000"));
    holding.describe(true, "숨긴 판단");
    var history = new SharedHoldingHistory(holding, SharedHoldingHistory.ChangeType.ADDED);
    var previous = LocalDate.of(2026, 9, 18);
    var current = LocalDate.of(2026, 9, 21);
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(previous, current));
    given(profileRepository.findAllByProfilePublicTrueAndLeagueEnabledTrue())
        .willReturn(List.of(profile));
    given(profile.getMember()).willReturn(member);
    given(member.getId()).willReturn(1L);
    given(profile.getLeagueEnabledAt()).willReturn(Instant.parse("2026-09-14T01:00:00Z"));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(portfolio.getId()).willReturn(2L);
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                2L, Instant.parse("2026-09-20T15:00:00Z")))
        .willReturn(List.of(history));
    given(priceRepository.findByAssetIdAndBaseDate(null, previous))
        .willReturn(Optional.of(new StockDailyPrice(asset, previous, new BigDecimal("70000"))));
    given(priceRepository.findByAssetIdAndBaseDate(null, current))
        .willReturn(Optional.of(new StockDailyPrice(asset, current, new BigDecimal("77000"))));

    assertThat(service.recalculate()).isEqualTo(1);
    var result = ArgumentCaptor.forClass(PortfolioDailyReturn.class);
    then(returnRepository).should().save(result.capture());
    assertThat(result.getValue().isEligible()).isTrue();
    assertThat(result.getValue().getDailyReturnRate()).isEqualByComparingTo("10.0000");
    assertThat(result.getValue().getStartingEvaluationAmount()).isEqualByComparingTo("140000");
  }

  @Test
  void enrollmentDoesNotRetroactivelyRankThePartialEnrollmentWeek() {
    var profile = mock(InvestorProfile.class);
    var member = mock(Member.class);
    given(priceRepository.findDistinctBaseDatesOrderByBaseDate())
        .willReturn(List.of(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22)));
    given(profileRepository.findAllByProfilePublicTrueAndLeagueEnabledTrue())
        .willReturn(List.of(profile));
    given(profile.getMember()).willReturn(member);
    given(member.getId()).willReturn(1L);
    given(profile.getLeagueEnabledAt()).willReturn(Instant.parse("2026-09-21T01:00:00Z"));
    given(portfolioRepository.findByMemberId(1L))
        .willReturn(Optional.of(mock(SharedPortfolio.class)));
    assertThat(service.recalculate()).isZero();
    then(historyRepository).shouldHaveNoInteractions();
    then(returnRepository).shouldHaveNoInteractions();
  }
}
