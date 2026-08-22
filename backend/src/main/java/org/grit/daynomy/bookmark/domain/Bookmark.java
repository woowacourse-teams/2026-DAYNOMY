package org.grit.daynomy.bookmark.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "bookmarks",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_bookmarks_member_target",
          columnNames = {"member_id", "target_id"})
    })
public class Bookmark {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  private Bookmark(Member member, Long targetId) {
    this.member = member;
    this.targetId = targetId;
  }

  public static Bookmark create(Member member, Long targetId) {
    return new Bookmark(member, targetId);
  }
}
