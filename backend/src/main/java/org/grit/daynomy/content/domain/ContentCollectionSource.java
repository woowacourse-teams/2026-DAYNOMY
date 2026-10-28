package org.grit.daynomy.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "content_collection_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_content_collection_sources_type_url",
            columnNames = {"source_type", "source_url"}))
public class ContentCollectionSource extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private ContentSourceType sourceType;

  @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
  private String sourceUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id")
  private Asset asset;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "last_collected_at")
  private Instant lastCollectedAt;

  public ContentCollectionSource(
      String name, ContentSourceType sourceType, String sourceUrl, Asset asset, boolean enabled) {
    this.name = name;
    this.sourceType = sourceType;
    this.sourceUrl = sourceUrl;
    this.asset = asset;
    this.enabled = enabled;
  }

  public void update(
      String name, ContentSourceType sourceType, String sourceUrl, Asset asset, boolean enabled) {
    boolean identityChanged = this.sourceType != sourceType || !this.sourceUrl.equals(sourceUrl);
    this.name = name;
    this.sourceType = sourceType;
    this.sourceUrl = sourceUrl;
    this.asset = asset;
    this.enabled = enabled;
    if (identityChanged) {
      this.lastCollectedAt = null;
    }
  }

  public void completeCollection(Instant collectedAt) {
    this.lastCollectedAt = collectedAt;
  }
}
