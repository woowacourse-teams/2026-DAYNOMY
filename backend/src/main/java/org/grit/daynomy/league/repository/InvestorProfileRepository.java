package org.grit.daynomy.league.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorProfileRepository extends JpaRepository<InvestorProfile, Long> {
  Optional<InvestorProfile> findByMemberId(Long memberId);

  Optional<InvestorProfile> findByPublicIdAndProfilePublicTrue(String publicId);

  List<InvestorProfile> findAllByProfilePublicTrueAndLeagueEnabledTrue();

  boolean existsByDisplayNameAndMemberIdNot(String displayName, Long memberId);
}
