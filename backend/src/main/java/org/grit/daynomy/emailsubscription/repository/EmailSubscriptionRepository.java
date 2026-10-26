package org.grit.daynomy.emailsubscription.repository;

import java.util.List;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {
  @Query(
      """
      select s.memberId from EmailSubscription s
      where s.status = org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus.SUBSCRIBED
        and s.member.withdrawnAt is null and s.memberId > :after order by s.memberId
      """)
  List<Long> findEligibleMemberIds(Long after, Pageable pageable);
}
