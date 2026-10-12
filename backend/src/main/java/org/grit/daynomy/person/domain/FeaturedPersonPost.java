package org.grit.daynomy.person.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.content.domain.ContentSourceType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "featured_person_posts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_featured_person_posts_person_url",
            columnNames = {"person_id", "url"}))
public class FeaturedPersonPost extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "person_id", nullable = false)
  private FeaturedPerson person;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private ContentSourceType sourceType;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "url", nullable = false, columnDefinition = "TEXT")
  private String url;

  private FeaturedPersonPost(
      FeaturedPerson person, ContentSourceType sourceType, String title, String url) {
    this.person = person;
    this.sourceType = sourceType;
    this.title = title;
    this.url = url;
  }

  public static FeaturedPersonPost create(
      FeaturedPerson person, ContentSourceType sourceType, String title, String url) {
    return new FeaturedPersonPost(person, sourceType, title, url);
  }
}
