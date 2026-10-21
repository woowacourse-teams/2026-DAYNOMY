package org.grit.daynomy.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "content_interactions")
public class ContentInteraction extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(
      name = "deduplication_key",
      nullable = false,
      length = 64,
      updatable = false,
      columnDefinition = "CHAR(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String deduplicationKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", nullable = false, length = 30, updatable = false)
  private ContentType contentType;

  @Column(name = "content_id", nullable = false, updatable = false)
  private Long contentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "interaction_type", nullable = false, length = 30, updatable = false)
  private ContentInteractionType interactionType;

  @Column(name = "interaction_date", nullable = false, updatable = false)
  private LocalDate interactionDate;

  private ContentInteraction(
      UUID eventId,
      String deduplicationKey,
      ContentType contentType,
      Long contentId,
      ContentInteractionType interactionType,
      LocalDate interactionDate) {
    this.eventId = eventId;
    this.deduplicationKey = deduplicationKey;
    this.contentType = contentType;
    this.contentId = contentId;
    this.interactionType = interactionType;
    this.interactionDate = interactionDate;
  }

  public static ContentInteraction create(
      UUID eventId,
      String deduplicationKey,
      ContentType contentType,
      Long contentId,
      ContentInteractionType interactionType,
      LocalDate interactionDate) {
    return new ContentInteraction(
        eventId, deduplicationKey, contentType, contentId, interactionType, interactionDate);
  }
}
