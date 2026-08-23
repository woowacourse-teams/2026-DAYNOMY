package org.grit.daynomy.asset.domain;

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

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(
    name = "asset_ranking_history",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_asset_ranking_history_ranked_date_ranking",
            columnNames = {"ranked_date", "ranking"}))
public class AssetRankingHistory extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @Column(name = "ranking", nullable = false)
  private int ranking;

  @Column(name = "ranked_date", nullable = false)
  private LocalDate rankedDate;

  public AssetRankingHistory(Asset asset, int ranking, LocalDate rankedDate) {
    this.asset = asset;
    this.ranking = ranking;
    this.rankedDate = rankedDate;
  }
}
