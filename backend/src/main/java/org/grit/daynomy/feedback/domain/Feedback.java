package org.grit.daynomy.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feedbacks")
public class Feedback extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "page_path", nullable = false, length = 2048)
  private String pagePath;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FeedbackStatus status;

  private Feedback(String content, String pagePath) {
    this.content = content;
    this.pagePath = pagePath;
    this.status = FeedbackStatus.PENDING;
  }

  public static Feedback create(String content, String pagePath) {
    return new Feedback(content, pagePath);
  }

  public void changeStatus(FeedbackStatus status) {
    this.status = status;
  }
}
