package org.grit.daynomy.emailsubscription.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {
  Optional<EmailDelivery> findByMemberIdAndDeliveryDate(Long memberId, LocalDate date);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
      UPDATE email_deliveries SET status = 'CANCELLED'
      WHERE status IN ('READY', 'RETRY_PENDING')
        AND analysis_ids @> jsonb_build_array(CAST(:analysisId AS BIGINT))
      """,
      nativeQuery = true)
  int cancelQueuedForAnalysis(Long analysisId);

  @Query(
      value =
          """
      SELECT id FROM email_deliveries
      WHERE status IN ('ACCEPTED', 'SENDING', 'UNKNOWN')
        AND analysis_ids @> jsonb_build_array(CAST(:analysisId AS BIGINT))
      ORDER BY delivery_date DESC, id
      """,
      nativeQuery = true)
  List<UUID> findCorrectionReviewIds(Long analysisId, Pageable pageable);

  @Query(
      value =
          """
      SELECT DISTINCT item->>'key' FROM email_deliveries d
      CROSS JOIN LATERAL jsonb_array_elements(d.supplement_digest->'events') item
      WHERE d.member_id = :memberId AND d.status IN ('ACCEPTED','SENDING','UNKNOWN')
        AND item->>'key' IN (:keys)
      """,
      nativeQuery = true)
  List<String> findCoveredEventKeys(Long memberId, List<String> keys);

  @Query(
      value =
          """
      SELECT EXISTS(SELECT 1 FROM email_deliveries d WHERE d.member_id = :memberId
        AND d.status IN ('ACCEPTED','SENDING','UNKNOWN')
        AND d.supplement_digest->'assets'->>'baseDate' = :baseDate)
      """,
      nativeQuery = true)
  boolean hasCoveredAssetSummary(Long memberId, String baseDate);

  @Query(
      value =
          """
      SELECT d.* FROM email_deliveries d
      WHERE d.member_id = :memberId AND d.status IN ('ACCEPTED', 'SENDING', 'UNKNOWN')
        AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(d.analysis_ids) item(value)
          JOIN email_common_stock_analyses a ON a.id = cast(item.value AS BIGINT)
          WHERE a.asset_id = :assetId)
      ORDER BY CASE WHEN d.status = 'ACCEPTED' THEN 1 ELSE 0 END,
        d.accepted_at DESC NULLS LAST, d.delivery_date DESC, d.id DESC LIMIT 1
      """,
      nativeQuery = true)
  Optional<EmailDelivery> findLastNoticeForAsset(Long memberId, Long assetId);

  @Query(
      """
      select d.id from EmailDelivery d where d.deliveryDate = :date and d.status in :statuses
        and d.nextAttemptAt <= :now order by d.nextAttemptAt, d.id
      """)
  List<UUID> findDueIds(
      LocalDate date, Instant now, List<EmailDelivery.Status> statuses, Pageable pageable);

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
