package org.grit.daynomy.person.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "featured_people",
    uniqueConstraints = @UniqueConstraint(name = "uk_featured_people_name", columnNames = "name"))
public class FeaturedPerson extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "x_profile_url", columnDefinition = "TEXT")
  private String xProfileUrl;

  @Column(name = "threads_profile_url", columnDefinition = "TEXT")
  private String threadsProfileUrl;

  @Column(name = "profile_image_url", columnDefinition = "TEXT")
  private String profileImageUrl;

  private FeaturedPerson(
      String name,
      String xProfileUrl,
      String threadsProfileUrl,
      String profileImageUrl) {
    this.name = name;
    this.xProfileUrl = xProfileUrl;
    this.threadsProfileUrl = threadsProfileUrl;
    this.profileImageUrl = profileImageUrl;
  }

  public static FeaturedPerson create(
      String name,
      String xProfileUrl,
      String threadsProfileUrl,
      String profileImageUrl) {
    return new FeaturedPerson(name, xProfileUrl, threadsProfileUrl, profileImageUrl);
  }

  public void update(
      String name,
      String xProfileUrl,
      String threadsProfileUrl,
      String profileImageUrl) {
    this.name = name;
    this.xProfileUrl = xProfileUrl;
    this.threadsProfileUrl = threadsProfileUrl;
    this.profileImageUrl = profileImageUrl;
  }
}
