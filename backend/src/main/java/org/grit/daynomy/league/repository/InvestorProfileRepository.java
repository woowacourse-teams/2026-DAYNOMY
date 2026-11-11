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

  @Query(
      "select p from InvestorProfile p where p.publicId = :publicId and p.profilePublic = true"
          + " and p.member.status = org.grit.daynomy.member.domain.MemberStatus.ACTIVE")
  Optional<InvestorProfile> findByPublicIdAndProfilePublicTrue(String publicId);

  @Query(
      """
      select new org.grit.daynomy.league.repository.RankingInvestor(
        p.id, p.member.id, p.publicId, p.displayName, p.experienceLevel,
        p.riskProfile, p.trackingStartedOn, period.startedAt, period.endedAt, period.performanceStartedAt)
      from InvestorProfile p left join p.participations period
      where p.profilePublic = true and p.leagueEnabled = true
        and p.member.status = org.grit.daynomy.member.domain.MemberStatus.ACTIVE
      order by p.id, period.startedAt
      """)
  List<RankingInvestor> findRankingInvestors();

  @Query(
      "select p.id from InvestorProfile p where p.profilePublic = true and p.leagueEnabled = true"
          + " and p.member.status = org.grit.daynomy.member.domain.MemberStatus.ACTIVE")
  List<Long> findPublicRankingProfileIds();

  @Query(
      "select p.id from InvestorProfile p where p.profilePublic = false or p.leagueEnabled = false"
          + " or p.member.status <> org.grit.daynomy.member.domain.MemberStatus.ACTIVE")
  List<Long> findNonPublicRankingProfileIds();

  @Query(
      """
      select new org.grit.daynomy.league.repository.RankingInvestor(
        p.id, p.member.id, p.publicId, p.displayName, p.experienceLevel,
        p.riskProfile, p.trackingStartedOn, null, null, null, p.member.profileImageUrl)
      from InvestorProfile p
      where p.id in :ids and p.profilePublic = true and p.leagueEnabled = true
        and p.member.status = org.grit.daynomy.member.domain.MemberStatus.ACTIVE
      """)
  List<RankingInvestor> findPublicRankingProfilesByIdIn(List<Long> ids);

  List<InvestorProfile> findAllByLeagueEnabledAtIsNotNull();

  boolean existsByDisplayNameAndMemberIdNot(String displayName, Long memberId);
}
