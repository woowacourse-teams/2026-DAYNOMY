package org.grit.daynomy.search.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.search.domain.NewsSearchSort;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.springframework.data.jpa.domain.Specification;

public record NewsSearchSpecification(
    NewsSearchTerms terms,
    Category category,
    NewsSearchSort sort,
    NewsStatus status,
    boolean adminSearch)
    implements Specification<News> {

  public NewsSearchSpecification(NewsSearchTerms terms, Category category, NewsSearchSort sort) {
    this(terms, category, sort, NewsStatus.PUBLISHED, false);
  }

  public static NewsSearchSpecification forAdmin(
      NewsSearchTerms terms, Category category, NewsStatus status, NewsSearchSort sort) {
    return new NewsSearchSpecification(terms, category, sort, status, true);
  }

  @Override
  public Predicate toPredicate(Root<News> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
    List<Predicate> predicates = new ArrayList<>();
    if (status != null) {
      predicates.add(builder.equal(root.get("status"), status));
    }
    if (category != null) {
      predicates.add(builder.equal(root.get("category"), category));
    }

    boolean relevance =
        sort == NewsSearchSort.RELEVANCE && query != null && query.getResultType() != Long.class;
    Expression<Integer> titleMatches = builder.literal(0);
    for (String term : terms.values()) {
      String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      Predicate titleMatch = builder.like(builder.lower(root.get("title")), pattern, '!');
      Predicate contentMatch = builder.like(builder.lower(root.get("content")), pattern, '!');
      predicates.add(builder.or(titleMatch, contentMatch));
      if (relevance) {
        titleMatches =
            builder.sum(
                titleMatches, builder.<Integer>selectCase().when(titleMatch, 1).otherwise(0));
      }
    }

    if (query != null && query.getResultType() != Long.class) {
      var latest =
          List.of(
              builder.desc(root.get(adminSearch ? "createdAt" : "publishedAt")),
              builder.desc(root.get("id")));
      if (relevance) {
        query.orderBy(builder.desc(titleMatches), latest.getFirst(), latest.getLast());
      } else {
        query.orderBy(latest);
      }
    }
    return builder.and(predicates.toArray(Predicate[]::new));
  }
}
