package org.grit.daynomy.search.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.ContentSearchItemResponse;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentSearchRepository {

  private static final String QUERY =
      """
      WITH external_raw AS (
        SELECT 'PERSON_POST' AS kind, p.id, p.source_type AS source, p.title,
               CAST(NULL AS text) AS body, p.created_at AS occurred_at, p.url,
               p.person_id, person.name AS person_name, CAST(NULL AS bigint) AS asset_id,
               CAST(NULL AS text) AS image_url, CAST(NULL AS text) AS category,
               CAST(NULL AS date) AS published_date
        FROM featured_person_posts p
        JOIN featured_people person ON person.id = p.person_id
        WHERE p.status = 'APPROVED'
        UNION ALL
        SELECT 'ASSET_CONTENT', c.id, c.source_type, c.title, CAST(NULL AS text),
               c.created_at, c.url, CAST(NULL AS bigint), CAST(NULL AS text), c.asset_id,
               c.image_url, CAST(NULL AS text), c.published_date
        FROM stock_related_contents c
        WHERE c.news_id IS NULL AND c.source_type IN ('YOUTUBE', 'THREADS', 'X')
      ),
      external_cleaned AS (
        SELECT e.*,
               regexp_replace(
                 split_part(split_part(e.url, '?', 1), '#', 1),
                 '^https?://(www\\.)?', '', 'i'
               ) AS stripped_url
        FROM external_raw e
      ),
      external_normalized AS (
        SELECT e.*,
               replace(
                 replace(lower(split_part(e.stripped_url, '/', 1)), 'twitter.com', 'x.com'),
                 'threads.net', 'threads.com'
               ) ||
               regexp_replace(
                 substr(e.stripped_url, length(split_part(e.stripped_url, '/', 1)) + 1),
                 '/$', ''
               ) AS plain_url
        FROM external_cleaned e
      ),
      external_keys AS (
        SELECT e.*,
               CASE
                 WHEN e.url ~* '^https?://(www\\.|m\\.)?youtu\\.be/' THEN
                   coalesce(
                     'youtube:' || substring(e.url FROM
                       '^https?://(?:www\\.|m\\.)?youtu\\.be/([A-Za-z0-9_-]{11})(?:[/#?]|$)'),
                     e.plain_url
                   )
                 WHEN e.url ~* '^https?://(www\\.|m\\.)?youtube\\.com/' THEN
                   coalesce(
                     'youtube:' || substring(e.url FROM '[?&]v=([A-Za-z0-9_-]{11})(?:[&#]|$)'),
                     'youtube:' || substring(e.url FROM
                       '/(?:embed|shorts|live)/([A-Za-z0-9_-]{11})(?:[/#?]|$)'),
                     e.plain_url
                   )
                 ELSE e.plain_url
               END AS url_key
        FROM external_normalized e
      ),
      external_ranked AS (
        SELECT e.*,
               row_number() OVER (
                 PARTITION BY e.url_key
                 ORDER BY CASE WHEN e.kind = 'PERSON_POST' THEN 0 ELSE 1 END,
                          e.occurred_at DESC, e.id DESC
               ) AS duplicate_rank
        FROM external_keys e
      ),
      all_contents AS (
        SELECT e.kind, e.id, e.source, e.title, e.body, e.occurred_at, e.url,
               e.person_id, e.person_name, e.asset_id, e.image_url, e.category,
               e.published_date
        FROM external_ranked e
        WHERE e.kind = 'PERSON_POST' OR e.duplicate_rank = 1
        UNION ALL
        SELECT 'NEWS', n.id, 'DAYNOMY', n.title, n.content, n.published_at,
               CAST(NULL AS text), CAST(NULL AS bigint), CAST(NULL AS text),
               CAST(NULL AS bigint), n.image_url, n.category, CAST(NULL AS date)
        FROM news n
        WHERE n.status = 'PUBLISHED'
      ),
      matched AS (
        SELECT *
        FROM all_contents
        WHERE (:source = 'ALL' OR source = :source)
        /* SEARCH_TERMS */
      )
      SELECT total.total_elements, item.*
      FROM (SELECT count(*) AS total_elements FROM matched) total
      LEFT JOIN LATERAL (
        SELECT * FROM matched
        ORDER BY occurred_at DESC NULLS LAST, kind, id
        LIMIT :size OFFSET :offset
      ) item ON true
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ContentSearchResponse search(
      NewsSearchTerms terms, ContentSearchSource source, int page, int size) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("source", source.name())
            .addValue("size", size)
            .addValue("offset", (long) (page - 1) * size);
    StringBuilder conditions = new StringBuilder();
    for (int index = 0; index < terms.values().size(); index++) {
      String name = "term" + index;
      String pattern =
          "%"
              + terms.values().get(index).replace("!", "!!").replace("%", "!%").replace("_", "!_")
              + "%";
      params.addValue(name, pattern);
      conditions
          .append(" AND (lower(title) LIKE :")
          .append(name)
          .append(" ESCAPE '!' OR lower(body) LIKE :")
          .append(name)
          .append(" ESCAPE '!')");
    }

    return jdbc.query(
        QUERY.replace("/* SEARCH_TERMS */", conditions.toString()),
        params,
        result -> {
          List<ContentSearchItemResponse> items = new ArrayList<>();
          long total = 0;
          while (result.next()) {
            total = result.getLong("total_elements");
            if (result.getObject("id") == null) continue;
            Timestamp date = result.getTimestamp("occurred_at");
            Date publishedDate = result.getDate("published_date");
            String category = result.getString("category");
            items.add(
                new ContentSearchItemResponse(
                    result.getString("kind"),
                    result.getLong("id"),
                    ContentSearchSource.valueOf(result.getString("source")),
                    result.getString("title"),
                    date == null ? null : date.toInstant(),
                    result.getString("url"),
                    result.getObject("person_id", Long.class),
                    result.getString("person_name"),
                    result.getObject("asset_id", Long.class),
                    result.getString("image_url"),
                    category == null ? null : Category.valueOf(category),
                    publishedDate == null ? null : publishedDate.toLocalDate()));
          }
          return new ContentSearchResponse(
              items, page, size, total, (int) ((total + size - 1) / size));
        });
  }
}
