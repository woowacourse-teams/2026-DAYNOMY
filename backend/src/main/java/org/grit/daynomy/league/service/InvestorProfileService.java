package org.grit.daynomy.league.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.dto.LeagueDto.ProfileRequest;
import org.grit.daynomy.league.dto.LeagueDto.ProfileResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicationRequest;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InvestorProfileService {

  private final InvestorProfileRepository profileRepository;
  private final MemberService memberService;

  @Transactional(readOnly = true)
  public ProfileResponse getMine(Long memberId) {
    return profileRepository.findByMemberId(memberId).map(ProfileResponse::from).orElse(null);
  }

  @Transactional
  public ProfileResponse save(Long memberId, ProfileRequest request) {
    Member member = memberService.updateNickname(memberId, request.displayName());
    String displayName = member.getNickname();
    InvestorProfile profile =
        profileRepository
            .findByMemberId(memberId)
            .orElseGet(
                () ->
                    InvestorProfile.create(
                        member,
                        displayName,
                        request.bio(),
                        request.experienceLevel(),
                        request.riskProfile()));
    profile.changeProfile(
        displayName, request.bio(), request.experienceLevel(), request.riskProfile());
    return ProfileResponse.from(profileRepository.save(profile));
  }

  @Transactional
  public ProfileResponse publish(Long memberId, PublicationRequest request) {
    if (request.leagueEnabled() && !request.profilePublic()) {
      throw new BusinessException(LeagueErrorCode.INVALID_LEAGUE_ENROLLMENT);
    }
    InvestorProfile profile =
        profileRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new BusinessException(LeagueErrorCode.PROFILE_REQUIRED));
    profile.changePublication(
        request.profilePublic(),
        request.leagueEnabled(),
        request.allocationPublic(),
        request.detailPublic(),
        Instant.now());
    return ProfileResponse.from(profile);
  }
}
