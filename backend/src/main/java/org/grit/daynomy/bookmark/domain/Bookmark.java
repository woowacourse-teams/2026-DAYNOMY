package org.grit.daynomy.bookmark.domain;

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
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "bookmarks",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_bookmarks_member_asset",
          columnNames = {"member_id", "asset_id"})
    })
public class Bookmark {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  private Bookmark(Member member, Asset asset) {
    this.member = member;
    this.asset = asset;
  }

  public static Bookmark create(Member member, Asset asset) {
    return new Bookmark(member, asset);
  }
}
