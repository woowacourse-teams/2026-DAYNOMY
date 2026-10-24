package org.grit.daynomy.league.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InvestorProfileRepository extends JpaRepository<InvestorProfile, Long> {
  Optional<InvestorProfile> findByMemberId(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InvestorProfile p where p.member.id = :memberId")
  Optional<InvestorProfile> findByMemberIdForUpdate(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InvestorProfile p where p.id = :id")
  Optional<InvestorProfile> findByIdForUpdate(Long id);

  Optional<InvestorProfile> findByPublicIdAndProfilePublicTrue(String publicId);

  @Query(
      """
      select new org.grit.daynomy.league.repository.RankingInvestor(
        p.id, p.member.id, p.publicId, p.displayName, p.experienceLevel,
        p.riskProfile, p.trackingStartedOn, period.startedAt, period.endedAt)
      from InvestorProfile p left join p.participations period
      where p.profilePublic = true and p.leagueEnabled = true
      order by p.id, period.startedAt
      """)
  List<RankingInvestor> findRankingInvestors();

  List<InvestorProfile> findAllByLeagueEnabledAtIsNotNull();

  boolean existsByDisplayNameAndMemberIdNot(String displayName, Long memberId);
}
