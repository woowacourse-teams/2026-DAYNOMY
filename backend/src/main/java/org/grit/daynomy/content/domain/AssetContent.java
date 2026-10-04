package org.grit.daynomy.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.news.domain.News;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "stock_related_contents",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_stock_related_contents_asset_url",
            columnNames = {"asset_id", "url"}))
public class AssetContent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "asset_id", nullable = false)
  private Asset asset;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "news_id")
  private News news;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private ContentSourceType sourceType;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "url", nullable = false, columnDefinition = "TEXT")
  private String url;

  private AssetContent(Asset asset, ContentSourceType sourceType, String title, String url) {
    this.asset = asset;
    this.sourceType = sourceType;
    this.title = title;
    this.url = url;
  }

  private AssetContent(
      Asset asset, News news, ContentSourceType sourceType, String title, String url) {
    this(asset, sourceType, title, url);
    this.news = news;
  }

  public static AssetContent create(
      Asset asset, ContentSourceType sourceType, String title, String url) {
    return new AssetContent(asset, sourceType, title, url);
  }

  public static AssetContent createInternalNews(Asset asset, News news) {
    return new AssetContent(
        asset,
        news,
        ContentSourceType.INTERNAL_NEWS,
        news.getTitle(),
        "/news/" + news.getId());
  }
}
