package org.grit.daynomy.finance.domain;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "learning_progress",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_learning_progress_member_item",
            columnNames = {"member_id", "item_key"}))
public class LearningProgress extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "item_key", nullable = false, length = 100)
  private String itemKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_type", nullable = false, length = 20)
  private LearningItemType itemType;

  @Column(nullable = false)
  private boolean completed;

  @Column(nullable = false)
  private boolean bookmarked;

  private LearningProgress(Member member, String itemKey, LearningItemType itemType) {
    this.member = Objects.requireNonNull(member);
    this.itemKey = Objects.requireNonNull(itemKey);
    this.itemType = Objects.requireNonNull(itemType);
  }

  public static LearningProgress create(Member member, String itemKey, LearningItemType itemType) {
    return new LearningProgress(member, itemKey, itemType);
  }

  public void change(LearningItemType itemType, boolean completed, boolean bookmarked) {
    this.itemType = Objects.requireNonNull(itemType);
    this.completed = completed;
    this.bookmarked = bookmarked;
  }
}
