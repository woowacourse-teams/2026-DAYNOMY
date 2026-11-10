package org.grit.daynomy.portfolio.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourcePolicy;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PortfolioAnalysisEvidenceVerifier {
  private final PortfolioAnalysisDocumentClient documents;

  public PortfolioAnalysisEvidenceVerifier(PortfolioAnalysisDocumentClient documents) {
    this.documents = documents;
  }

  PortfolioAnalysisEvidence prepare(
      Map<String, List<String>> urls, List<PortfolioAnalysisResult.Source> discovered) {
    var verifiedUrls =
        discovered.stream()
            .map(source -> PortfolioAnalysisSourcePolicy.normalize(source.url()))
            .collect(Collectors.toSet());
    var candidates =
        urls.values().stream()
            .flatMap(List::stream)
            .filter(url -> !PortfolioAnalysisSourcePolicy.isIndex(url))
            .filter(url -> verifiedUrls.contains(PortfolioAnalysisSourcePolicy.normalize(url)))
            .collect(
                Collectors.toMap(
                    PortfolioAnalysisSourcePolicy::normalize,
                    url -> url,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    var fetched = new LinkedHashMap<>(documents.fetchAll(List.copyOf(candidates.values())));
    var selectedKeys =
        urls.values().stream()
            .flatMap(List::stream)
            .map(PortfolioAnalysisSourcePolicy::normalize)
            .collect(Collectors.toSet());
    var failedSelected =
        candidates.entrySet().stream()
            .filter(entry -> selectedKeys.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .filter(url -> fetched.getOrDefault(url, Optional.empty()).isEmpty())
            .toList();
    if (!failedSelected.isEmpty()) fetched.putAll(documents.fetchAll(failedSelected));
    Map<String, PortfolioAnalysisSourceDocument> prepared = new LinkedHashMap<>();
    fetched.forEach(
        (url, document) ->
            document.filter(this::usable).ifPresent(value -> prepared.put(url, value)));
    return PortfolioAnalysisEvidence.from(urls, prepared);
  }

  private boolean usable(PortfolioAnalysisSourceDocument value) {
    return !value.body().isBlank()
        && !value.isAnnouncementOnly()
        && !value.body().strip().equals("You need to enable JavaScript to run this app.")
        && (value.publishedOn() == null
            || !value
                .publishedOn()
                .isAfter(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))));
  }

  public PortfolioAnalysisResult verify(PortfolioAnalysisResult result) {
    long started = System.nanoTime();
    var urls =
        result.impacts().stream()
            .flatMap(impact -> impact.sources().stream())
            .map(PortfolioAnalysisResult.Source::url)
            .distinct()
            .toList();
    return verify(result, documents.fetchAll(urls), started);
  }

  PortfolioAnalysisResult verifyPrepared(
      PortfolioAnalysisResult result, Map<String, PortfolioAnalysisSourceDocument> prepared) {
    Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched = new LinkedHashMap<>();
    prepared.forEach((url, document) -> fetched.put(url, Optional.of(document)));
    return verify(result, fetched, System.nanoTime());
  }

  private PortfolioAnalysisResult verify(
      PortfolioAnalysisResult result,
      Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched,
      long started) {
    List<PortfolioAnalysisResult.AssetImpactResult> impacts = new ArrayList<>();
    int rejected = 0;
    Map<String, Integer> rejectionReasons = new LinkedHashMap<>();
    for (var impact : result.impacts()) {
      String failure = impact.direction() == null ? null : rejectionReason(impact, fetched);
      if (failure == null) {
        impacts.add(impact.direction() == null ? impact : withVerifiedDates(impact, fetched));
        continue;
      }
      rejected++;
      rejectionReasons.merge(failure, 1, Integer::sum);
      String message = "원문과 사건 시점을 확인하지 못해 판단을 보류했어요.";
      impacts.add(
          new PortfolioAnalysisResult.AssetImpactResult(
              impact.assetName(),
              null,
              null,
              message,
              "",
              "",
              message,
              message,
              List.of(),
              impact.sortOrder()));
    }
    impacts.sort(
        Comparator.comparing(
            PortfolioAnalysisResult.AssetImpactResult::impactLevel,
            Comparator.nullsLast(Comparator.naturalOrder())));
    List<PortfolioAnalysisResult.AssetImpactResult> ordered = new ArrayList<>();
    for (var impact : impacts) {
      ordered.add(
          new PortfolioAnalysisResult.AssetImpactResult(
              impact.assetName(),
              impact.direction(),
              impact.impactLevel(),
              impact.issueSummary(),
              impact.expectedReaction(),
              impact.outlook(),
              impact.reason(),
              impact.evidenceSentence(),
              impact.sources(),
              ordered.size() + 1));
    }
    var sources = ordered.stream().flatMap(impact -> impact.sources().stream()).distinct().toList();
    log.atInfo()
        .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_SOURCES_VERIFIED.code())
        .addKeyValue("durationMs", Duration.ofNanos(System.nanoTime() - started).toMillis())
        .addKeyValue("sourceCount", fetched.size())
        .addKeyValue("rejectedAssetCount", rejected)
        .addKeyValue("rejectionReasons", rejectionReasons)
        .log(LogEvent.PORTFOLIO_ANALYSIS_SOURCES_VERIFIED.message());
    return new PortfolioAnalysisResult(
        rejected == 0 ? result.overallImpact() : "", ordered, sources);
  }

  private String rejectionReason(
      PortfolioAnalysisResult.AssetImpactResult impact,
      Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched) {
    if (impact.sources().isEmpty()) return "SOURCE_MISSING";
    String claims =
        String.join(" ", impact.issueSummary(), impact.reason(), impact.evidenceSentence());
    var contexts = new ArrayList<String>();
    var titles = new ArrayList<String>();
    for (var source : impact.sources()) {
      var document = fetched.getOrDefault(source.url(), Optional.empty());
      if (document.isEmpty()) return "SOURCE_UNAVAILABLE";
      String failure =
          document
              .get()
              .rejectionReason(
                  source.factQuote(),
                  document.get().publishedOn(),
                  document.get().confirmedOccurrence(source.factQuote(), source.occurredOn()),
                  source.excerpt());
      if (failure != null) return failure;
      contexts.add(document.get().contextForQuote(source.factQuote()));
      titles.add(document.get().title());
    }
    String context = String.join(" ", contexts);
    return new PortfolioAnalysisSourceDocument(String.join(" ", titles), context, null)
        .rejectionReason(context, null, null, claims);
  }

  private PortfolioAnalysisResult.AssetImpactResult withVerifiedDates(
      PortfolioAnalysisResult.AssetImpactResult impact,
      Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched) {
    var sources =
        impact.sources().stream()
            .map(
                source -> {
                  var document = fetched.get(source.url()).orElseThrow();
                  return new PortfolioAnalysisResult.Source(
                      source.title(),
                      source.url(),
                      document.publishedOn(),
                      document.confirmedOccurrence(source.factQuote(), source.occurredOn()),
                      source.excerpt(),
                      source.factQuote());
                })
            .toList();
    return new PortfolioAnalysisResult.AssetImpactResult(
        impact.assetName(),
        impact.direction(),
        impact.impactLevel(),
        impact.issueSummary(),
        impact.expectedReaction(),
        impact.outlook(),
        impact.reason(),
        impact.evidenceSentence(),
        sources,
        impact.sortOrder());
  }
}
