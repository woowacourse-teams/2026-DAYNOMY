package org.grit.daynomy.keyword.parser;

import org.grit.daynomy.keyword.domain.Keyword;
import org.grit.daynomy.keyword.entity.NewsKeywordEntity;
import org.grit.daynomy.news.domain.News;

public final class KeywordParser {

  private KeywordParser() {}

  public static Keyword toDomain(NewsKeywordEntity entity) {
    return new Keyword(entity.getKeyword(), entity.getDescription());
  }

  public static NewsKeywordEntity toEntity(News news, Keyword keyword) {
    return new NewsKeywordEntity(news, keyword.getKeyword(), keyword.getDescription());
  }
}
