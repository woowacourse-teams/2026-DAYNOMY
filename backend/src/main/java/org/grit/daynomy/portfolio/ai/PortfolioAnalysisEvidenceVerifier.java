package org.grit.daynomy.portfolio.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PortfolioAnalysisEvidenceVerifier {
  private final PortfolioAnalysisDocumentClient documents;

  public PortfolioAnalysisEvidenceVerifier(PortfolioAnalysisDocumentClient documents) {
    this.documents = documents;
  }

  public PortfolioAnalysisResult verify(PortfolioAnalysisResult result) {
    long started = System.nanoTime();
    long deadline = started + Duration.ofSeconds(5).toNanos();
    Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched = fetchSources(result, deadline);
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

  private Map<String, Optional<PortfolioAnalysisSourceDocument>> fetchSources(
      PortfolioAnalysisResult result, long deadline) {
    var urls =
        result.impacts().stream()
            .filter(impact -> impact.direction() != null)
            .flatMap(impact -> impact.sources().stream())
            .map(PortfolioAnalysisResult.Source::url)
            .distinct()
            .toList();
    Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched = new LinkedHashMap<>();
    try (var executor = Executors.newFixedThreadPool(4, Thread.ofVirtual().factory())) {
      var tasks =
          urls.stream()
              .<java.util.concurrent.Callable<Optional<PortfolioAnalysisSourceDocument>>>map(
                  url -> () -> documents.fetch(url, Duration.ofNanos(deadline - System.nanoTime())))
              .toList();
      var futures =
          executor.invokeAll(
              tasks, Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
      for (int index = 0; index < urls.size(); index++) {
        try {
          fetched.put(urls.get(index), futures.get(index).get());
        } catch (CancellationException | ExecutionException exception) {
          fetched.put(urls.get(index), Optional.empty());
        }
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
    urls.forEach(url -> fetched.putIfAbsent(url, Optional.empty()));
    return fetched;
  }

  private String rejectionReason(
      PortfolioAnalysisResult.AssetImpactResult impact,
      Map<String, Optional<PortfolioAnalysisSourceDocument>> fetched) {
    if (impact.sources().isEmpty()) return "SOURCE_MISSING";
    String claims =
        String.join(" ", impact.issueSummary(), impact.reason(), impact.evidenceSentence());
    for (var source : impact.sources()) {
      var document = fetched.get(source.url());
      if (document.isEmpty()) return "SOURCE_UNAVAILABLE";
      String failure =
          document
              .get()
              .rejectionReason(
                  source.factQuote(),
                  document.get().publishedOn(),
                  document.get().confirmedOccurrence(source.factQuote(), source.occurredOn()),
                  claims);
      if (failure != null) return failure;
    }
    return null;
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
