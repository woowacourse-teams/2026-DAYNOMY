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
import org.springframework.data.jpa.repository.Query;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {
  Optional<EmailDelivery> findByMemberIdAndDeliveryDate(Long memberId, LocalDate date);

  interface LastStockNotice {
    Long getAssetId();

    Instant getAcceptedAt();
  }

  @Query(
      value =
          """
      SELECT CAST(item->>'assetId' AS BIGINT) AS "assetId", MAX(d.accepted_at) AS "acceptedAt"
      FROM email_deliveries d CROSS JOIN LATERAL jsonb_array_elements(d.stock_digest->'stocks') item
      WHERE d.member_id = :memberId AND d.status = 'ACCEPTED'
        AND CAST(item->>'assetId' AS BIGINT) IN (:assetIds)
      GROUP BY item->>'assetId'
      """,
      nativeQuery = true)
  List<LastStockNotice> findLastStockNotices(Long memberId, List<Long> assetIds);

  @Query(
      value =
          """
      SELECT DISTINCT CAST(item->>'assetId' AS BIGINT) FROM email_deliveries d
      CROSS JOIN LATERAL jsonb_array_elements(d.stock_digest->'stocks') item
      WHERE d.member_id = :memberId AND d.status IN ('ACCEPTED','SENDING','UNKNOWN')
        AND d.stock_digest->>'baseDate' = :baseDate
        AND CAST(item->>'assetId' AS BIGINT) IN (:assetIds)
      """,
      nativeQuery = true)
  List<Long> findCoveredPriceAssetIds(Long memberId, String baseDate, List<Long> assetIds);

  @Query(
      value =
          """
      SELECT DISTINCT item->>'key' FROM email_deliveries d
      CROSS JOIN LATERAL jsonb_array_elements(d.stock_digest->'contents') item
      WHERE d.member_id = :memberId AND d.status IN ('ACCEPTED','SENDING','UNKNOWN')
        AND item->>'key' IN (:keys)
      """,
      nativeQuery = true)
  List<String> findCoveredContentKeys(Long memberId, List<String> keys);

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
      SELECT EXISTS(SELECT 1 FROM email_deliveries d WHERE d.member_id = :memberId
        AND d.status IN ('ACCEPTED','SENDING','UNKNOWN')
        AND d.supplement_digest->'analysis'->>'id' = :analysisId)
      """,
      nativeQuery = true)
  boolean hasCoveredPortfolioAnalysis(Long memberId, String analysisId);

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
}
