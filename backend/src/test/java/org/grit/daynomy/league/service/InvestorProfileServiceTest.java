package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.dto.LeagueDto.PublicationRequest;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.member.domain.Member;
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
  void disablingProfileRemovesEnrollmentTimeAndDetailPublication() {
    InvestorProfile profile =
        InvestorProfile.create(
            mock(Member.class),
            "차분한초보",
            "손실 조건부터 기록합니다.",
            ExperienceLevel.BEGINNER,
            RiskProfile.BALANCED);
    profile.changePublication(true, true, true, true, java.time.Instant.now());
    given(profileRepository.findByMemberId(1L)).willReturn(Optional.of(profile));

    var response = service.publish(1L, new PublicationRequest(false, false, false, true));

    assertThat(response.profilePublic()).isFalse();
    assertThat(response.leagueEnabled()).isFalse();
    assertThat(response.detailPublic()).isFalse();
    assertThat(response.leagueEnabledAt()).isNull();
  }
}
