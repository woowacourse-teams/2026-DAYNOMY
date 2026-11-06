package org.grit.daynomy.news.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.news.ai.NewsGenerationTopicResearch;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "news_generation_topics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsGenerationTopic extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "topic_key", nullable = false, length = 64, unique = true)
  private String topicKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "generation_type", nullable = false, length = 20)
  private NewsGenerationType generationType;

  @Column(nullable = false)
  private String title;

  @Column(name = "reference_date", nullable = false)
  private LocalDate referenceDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Category category;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<String> facts;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<NewsSourceInfo> sources;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NewsGenerationTopicStatus status;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "news_id", unique = true)
  private News news;

  private NewsGenerationTopic(
      String topicKey,
      NewsGenerationType generationType,
      String title,
      LocalDate referenceDate,
      Category category,
      List<String> facts,
      List<NewsSourceInfo> sources) {
    if (topicKey == null || !topicKey.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("Topic key must be a SHA-256 hex value.");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Topic title must not be blank.");
    }
    if (facts == null || facts.size() < 3) {
      throw new IllegalArgumentException("Topic must have at least three facts.");
    }
    if (sources == null || sources.size() < 2 || sources.size() > 4) {
      throw new IllegalArgumentException("Topic must have between two and four sources.");
    }
    this.topicKey = topicKey;
    this.generationType = Objects.requireNonNull(generationType);
    this.title = title;
    this.referenceDate = Objects.requireNonNull(referenceDate);
    this.category = Objects.requireNonNull(category);
    this.facts = List.copyOf(facts);
    this.sources = List.copyOf(sources);
    this.status = NewsGenerationTopicStatus.READY;
  }

  public static NewsGenerationTopic create(
      String topicKey,
      NewsGenerationType generationType,
      String title,
      LocalDate referenceDate,
      Category category,
      List<String> facts,
      List<NewsSourceInfo> sources) {
    return new NewsGenerationTopic(
        topicKey, generationType, title, referenceDate, category, facts, sources);
  }

  public void reserve() {
    if (status != NewsGenerationTopicStatus.READY) {
      throw new IllegalStateException("Only ready topics can be reserved.");
    }
    status = NewsGenerationTopicStatus.RESERVED;
  }

  public void generated(News generatedNews) {
    if (status != NewsGenerationTopicStatus.RESERVED) {
      throw new IllegalStateException("Only reserved topics can be completed.");
    }
    if (generatedNews == null) {
      throw new IllegalArgumentException("Generated news must not be null.");
    }
    news = generatedNews;
    status = NewsGenerationTopicStatus.GENERATED;
  }

  public void release() {
    if (status != NewsGenerationTopicStatus.RESERVED) {
      throw new IllegalStateException("Only reserved topics can be released.");
    }
    status = NewsGenerationTopicStatus.READY;
  }

  public boolean isReserved() {
    return status == NewsGenerationTopicStatus.RESERVED;
  }

  public NewsGenerationTopicResearch toResearch() {
    return new NewsGenerationTopicResearch(
        topicKey, title, referenceDate, category, facts, sources);
  }
}
