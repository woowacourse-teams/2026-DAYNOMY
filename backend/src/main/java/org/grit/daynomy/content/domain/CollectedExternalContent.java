package org.grit.daynomy.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "collected_external_contents",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_collected_external_contents_url", columnNames = "url"))
public class CollectedExternalContent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_id", nullable = false)
  private ContentCollectionSource source;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "url", nullable = false, columnDefinition = "TEXT")
  private String url;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Column(name = "published_date")
  private LocalDate publishedDate;

  public CollectedExternalContent(
      ContentCollectionSource source,
      String title,
      String description,
      String url,
      String imageUrl,
      LocalDate publishedDate) {
    this.source = source;
    this.title = title;
    this.description = description;
    this.url = url;
    this.imageUrl = imageUrl;
    this.publishedDate = publishedDate;
  }

  public void confirmPublishedDate(LocalDate confirmedDate) {
    if (publishedDate == null && confirmedDate != null) {
      publishedDate = confirmedDate;
    }
  }
}
