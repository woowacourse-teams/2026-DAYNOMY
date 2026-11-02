package org.grit.daynomy.league.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideEvidence;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.grit.daynomy.news.service.NewsService;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.service.ContentSearchService;
import org.jsoup.Jsoup;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class LeagueReviewEvidenceService {
  private final AssetContentRepository assetContents;
  private final ContentSearchService search;
  private final NewsService news;
  private final Clock clock;

  public LeagueReviewEvidenceService(
      AssetContentRepository assetContents,
      ContentSearchService search,
      NewsService news,
      Clock clock) {
    this.assetContents = assetContents;
    this.search = search;
    this.news = news;
    this.clock = clock;
  }

  public List<Candidate> search(Long assetId, String assetName, Instant savedAt, String focusTerm) {
    Instant now = clock.instant();
    Instant after = savedAt == null ? now : savedAt;
    Instant ninetyDaysAgo = now.minus(90, ChronoUnit.DAYS);
    if (after.isBefore(ninetyDaysAgo)) after = ninetyDaysAgo;
    if (!after.isBefore(now)) return List.of();

    var linked =
        assetContents.findPublishedNewsIdsForAsset(
            assetId, NewsStatus.PUBLISHED, after, now, PageRequest.of(0, 20));
    LinkedHashSet<Long> ids = new LinkedHashSet<>(linked);
    String focus = focusTerm == null ? "" : focusTerm.replaceAll("[^가-힣a-zA-Z0-9 ]", " ").trim();
    if (focus.length() > 40) focus = focus.substring(0, 40).trim();
    String keyword = focus.isBlank() ? assetName : assetName + " " + focus;
    search
        .search(keyword, ContentSearchSource.DAYNOMY, 1, 20, ContentSearchSort.LATEST)
        .items()
        .stream()
        .filter(item -> "NEWS".equals(item.kind()))
        .forEach(item -> ids.add(item.id()));

    List<Scored> result = new ArrayList<>();
    for (Long id : ids) {
      org.grit.daynomy.news.dto.NewsDetailResponse detail;
      try {
        detail = news.getNewsDetail(id);
      } catch (BusinessException exception) {
        if (exception.errorCode() == NewsErrorCode.NEWS_NOT_FOUND) continue;
        throw exception;
      }
      if (detail.publishedAt() == null
          || !detail.publishedAt().isAfter(after)
          || detail.publishedAt().isAfter(now)) continue;
      String body =
          Jsoup.parse(detail.content() == null ? "" : detail.content())
              .text()
              .replaceAll("\\s+", " ")
              .trim();
      if (body.isBlank()) continue;
      String excerpt = window(body, focus.isBlank() ? assetName : focus, 160);
      String context = window(body, focus.isBlank() ? assetName : focus, 800);
      var evidence =
          new ReviewGuideEvidence(
              "NEWS:" + id, detail.title(), detail.publishedAt(), excerpt, "/news/" + id);
      boolean focusMatch =
          !focus.isBlank() && (detail.title().contains(focus) || body.contains(focus));
      result.add(new Scored(new Candidate(evidence, context), linked.contains(id), focusMatch));
    }
    return result.stream()
        .sorted(
            Comparator.comparing(Scored::linked)
                .reversed()
                .thenComparing(Scored::focusMatch, Comparator.reverseOrder())
                .thenComparing(
                    (Scored item) -> item.candidate().evidence().publishedAt(),
                    Comparator.reverseOrder()))
        .limit(5)
        .map(Scored::candidate)
        .toList();
  }

  private String window(String body, String term, int limit) {
    if (body.length() <= limit) return body;
    int match = body.toLowerCase().indexOf(term.toLowerCase());
    int start = match < 0 ? 0 : Math.max(0, match - limit / 4);
    return body.substring(start, Math.min(body.length(), start + limit));
  }

  public record Candidate(ReviewGuideEvidence evidence, String modelContext) {}

  private record Scored(Candidate candidate, boolean linked, boolean focusMatch) {}
}
