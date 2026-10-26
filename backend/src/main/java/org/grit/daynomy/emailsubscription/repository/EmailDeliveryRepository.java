package org.grit.daynomy.emailsubscription.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {
  Optional<EmailDelivery> findByMemberIdAndDeliveryDate(Long memberId, LocalDate date);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from EmailDelivery d where d.id = :id")
  Optional<EmailDelivery> findByIdForUpdate(UUID id);

  @Query(
      value =
          """
      SELECT DISTINCT cast(items.value AS BIGINT) FROM email_deliveries d
      CROSS JOIN LATERAL jsonb_array_elements_text(d.analysis_ids) items(value)
      WHERE d.member_id = :memberId AND d.status IN ('ACCEPTED', 'SENDING', 'UNKNOWN')
        AND cast(items.value AS BIGINT) IN (:analysisIds)
      """,
      nativeQuery = true)
  List<Long> findCoveredAnalysisIds(Long memberId, List<Long> analysisIds);
}
