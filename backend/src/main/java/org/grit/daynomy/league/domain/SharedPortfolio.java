package org.grit.daynomy.league.domain;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

/** 원본 portfolios와 쓰기 경로를 공유하지 않는 리그 전용 자산 사본. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "shared_portfolios", uniqueConstraints = @UniqueConstraint(columnNames = "member_id"))
public class SharedPortfolio extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  public static SharedPortfolio create(Member member) {
    SharedPortfolio portfolio = new SharedPortfolio();
    portfolio.member = Objects.requireNonNull(member);
    return portfolio;
  }
}
