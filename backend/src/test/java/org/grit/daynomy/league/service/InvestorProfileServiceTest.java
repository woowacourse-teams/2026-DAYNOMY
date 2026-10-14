package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.PublicationRequest;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestorProfileServiceTest {

  @Mock private InvestorProfileRepository profileRepository;
  @Mock private MemberService memberService;
  @Mock private MemberRepository memberRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private InvestmentDecisionRepository decisionRepository;
  @InjectMocks private InvestorProfileService service;

  @Test
  void leagueCannotBeEnabledWhileProfileIsPrivate() {
    PublicationRequest request = new PublicationRequest(false, true, false, false);

    assertThatThrownBy(() -> service.publish(1L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(LeagueErrorCode.INVALID_LEAGUE_ENROLLMENT));
  }

  @Test
  void disablingProfilePreservesFirstEnrollmentAndClosesParticipation() {
    InvestorProfile profile =
        InvestorProfile.create(
            mock(Member.class),
            "차분한초보",
            "손실 조건부터 기록합니다.",
            ExperienceLevel.BEGINNER,
            RiskProfile.BALANCED);
    profile.changePublication(true, true, true, true, java.time.Instant.now());
    given(profileRepository.findByMemberIdForUpdate(1L)).willReturn(Optional.of(profile));
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(mock(Member.class)));

    var response = service.publish(1L, new PublicationRequest(false, false, false, true));

    assertThat(response.profilePublic()).isFalse();
    assertThat(response.leagueEnabled()).isFalse();
    assertThat(response.detailPublic()).isFalse();
    assertThat(response.leagueEnabledAt()).isNotNull();
    assertThat(profile.getParticipations().getFirst().getEndedAt()).isNotNull();
  }

  @Test
  void reenrollmentKeepsOriginalTrackingAndRecordsOnlyRealParticipationChanges() {
    var profile =
        InvestorProfile.create(
            mock(Member.class), "기록 투자자", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    var first = java.time.Instant.parse("2026-09-01T00:00:00Z");
    profile.changePublication(true, true, true, false, first);
    profile.startTracking(java.time.LocalDate.of(2026, 9, 1));
    profile.changePublication(true, true, false, true, first.plusSeconds(1));
    assertThat(profile.getParticipations()).hasSize(1);
    profile.changePublication(false, false, false, false, first.plusSeconds(86400));
    profile.changePublication(true, true, false, false, first.plusSeconds(86400 * 7));
    assertThat(profile.getLeagueEnabledAt()).isEqualTo(first);
    assertThat(profile.getTrackingStartedOn()).isEqualTo(java.time.LocalDate.of(2026, 9, 1));
    assertThat(profile.getParticipations()).hasSize(2);
    assertThat(profile.getParticipations().getFirst().contains(first.plusSeconds(86400 * 3)))
        .isFalse();
  }

  @Test
  void enrollmentRequiresImportedHoldings() {
    prepareProfile();
    assertThatThrownBy(() -> service.publish(1L, new PublicationRequest(true, true, false, false)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.LEAGUE_HOLDINGS_REQUIRED));
  }

  @Test
  void hiddenHoldingsAlsoRequireAnInvestmentReason() {
    prepareProfile();
    var portfolio = SharedPortfolio.create(mock(Member.class));
    var holding = new SharedHolding(portfolio, mock(Asset.class), 2, BigDecimal.TEN);
    holding.changeVisibility(true);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(null)).willReturn(List.of(holding));
    assertThatThrownBy(() -> service.publish(1L, new PublicationRequest(true, true, false, false)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.errorCode()).isEqualTo(LeagueErrorCode.HOLDING_REASON_REQUIRED));
  }

  @Test
  void existingReasonsArePreservedAndDetailedPublicationIsNotMandatory() {
    prepareProfile();
    var portfolio = SharedPortfolio.create(mock(Member.class));
    var holding = new SharedHolding(portfolio, mock(Asset.class), 2, BigDecimal.TEN);
    holding.describe(true, "기존 보유 이유");
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(holdingRepository.findAllByPortfolioIdOrderById(null)).willReturn(List.of(holding));
    var saved = service.publish(1L, new PublicationRequest(true, true, false, false));
    assertThat(saved.leagueEnabled()).isTrue();
    assertThat(saved.detailPublic()).isFalse();
    assertThat(holding.getReason()).isEqualTo("기존 보유 이유");
  }

  private void prepareProfile() {
    var member = mock(Member.class);
    var profile =
        InvestorProfile.create(member, "기록 초보", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(profileRepository.findByMemberIdForUpdate(1L)).willReturn(Optional.of(profile));
  }
}
