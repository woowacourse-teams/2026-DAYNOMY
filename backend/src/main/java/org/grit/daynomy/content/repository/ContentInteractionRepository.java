package org.grit.daynomy.content.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.grit.daynomy.content.domain.ContentInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentInteractionRepository extends JpaRepository<ContentInteraction, Long> {

  @Modifying
  @Query(
      value =
          """
          INSERT INTO content_interactions (
              event_id, deduplication_key, content_type, content_id, interaction_type,
              interaction_date, created_at, updated_at
          ) VALUES (
              :eventId, :deduplicationKey, :contentType, :contentId,
              :interactionType, :interactionDate, :createdAt, :createdAt
          )
          ON CONFLICT DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("eventId") UUID eventId,
      @Param("deduplicationKey") String deduplicationKey,
      @Param("contentType") String contentType,
      @Param("contentId") Long contentId,
      @Param("interactionType") String interactionType,
      @Param("interactionDate") LocalDate interactionDate,
      @Param("createdAt") Instant createdAt);

  @Query(
      value =
          """
          SELECT content_type AS "contentType",
                 content_id AS "contentId",
                 SUM(CASE WHEN interaction_type IN ('OPEN', 'EXPAND') THEN 1 ELSE 3 END) AS "score",
                 MAX(created_at) AS "lastInteractedAt"
          FROM content_interactions
          WHERE interaction_date >= :fromDate
          GROUP BY content_type, content_id
          ORDER BY "score" DESC, "lastInteractedAt" DESC, content_type ASC, content_id DESC
          """,
      countQuery =
          """
          SELECT COUNT(*)
          FROM (
              SELECT 1
              FROM content_interactions
              WHERE interaction_date >= :fromDate
              GROUP BY content_type, content_id
          ) AS ranked_contents
          """,
      nativeQuery = true)
  Page<ContentPopularityProjection> findPopularitySince(
      @Param("fromDate") LocalDate fromDate, Pageable pageable);
}
