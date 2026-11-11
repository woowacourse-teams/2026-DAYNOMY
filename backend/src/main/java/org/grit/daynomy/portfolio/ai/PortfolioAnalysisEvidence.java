package org.grit.daynomy.portfolio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;

/** 한 분석 요청에서 읽은 원문과 인용 문장을 보관한다. AI는 문장을 다시 쓰지 않고 참조한다. */
public final class PortfolioAnalysisEvidence {
  private final Map<String, Map<String, Quote>> byAsset;
  private final Map<String, PortfolioAnalysisSourceDocument> documents;
  private final List<String> referenceIds;
  private final Map<String, Integer> referenceNumbers;
  private final Map<String, List<String>> requestedUrls;
  private final Map<String, String> riskSearchStatuses;

  private PortfolioAnalysisEvidence(
      Map<String, Map<String, Quote>> byAsset,
      Map<String, PortfolioAnalysisSourceDocument> documents,
      Map<String, List<String>> requestedUrls) {
    this(byAsset, documents, requestedUrls, Map.of());
  }

  private PortfolioAnalysisEvidence(
      Map<String, Map<String, Quote>> byAsset,
      Map<String, PortfolioAnalysisSourceDocument> documents,
      Map<String, List<String>> requestedUrls,
      Map<String, String> riskSearchStatuses) {
    this.riskSearchStatuses = Map.copyOf(riskSearchStatuses);
    this.requestedUrls = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(requestedUrls));
    this.byAsset = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byAsset));
    this.documents = Map.copyOf(documents);
    this.referenceIds =
        byAsset.values().stream().flatMap(quotes -> quotes.keySet().stream()).distinct().toList();
    Map<String, Integer> numbers = new LinkedHashMap<>();
    for (int index = 0; index < referenceIds.size(); index++) {
      numbers.put(referenceIds.get(index), index + 1);
    }
    this.referenceNumbers = Map.copyOf(numbers);
  }

  static PortfolioAnalysisEvidence from(
      Map<String, List<String>> urls, Map<String, PortfolioAnalysisSourceDocument> documents) {
    Map<String, Map<String, Quote>> assets = new LinkedHashMap<>();
    for (var asset : urls.entrySet()) {
      Map<String, Quote> quotes = new LinkedHashMap<>();
      for (String url : asset.getValue()) {
        var document = documents.get(url);
        if (document == null || document.body().isBlank()) continue;
        for (String sentence : document.body().split("(?<=[.!?。])\\s+|\\R")) {
          if (sentence.isBlank()) continue;
          quotes.put(reference(url, sentence.strip()), new Quote(url, sentence.strip()));
        }
      }
      assets.put(asset.getKey(), java.util.Collections.unmodifiableMap(quotes));
    }
    return new PortfolioAnalysisEvidence(assets, documents, urls);
  }

  static String reference(String url, String sentence) {
    return java.util
        .UUID
        .nameUUIDFromBytes(
            (org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourcePolicy.normalize(url)
                    + "\n"
                    + sentence)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }

  Map<String, Object> input(List<PortfolioAnalysisTarget> targets) {
    return input(targets, Map.of());
  }

  Map<String, Object> input(
      List<PortfolioAnalysisTarget> targets, Map<String, List<String>> selectedReferences) {
    var assets = new ArrayList<Map<String, Object>>();
    Map<String, Map<String, Quote>> grouped = new LinkedHashMap<>();
    for (var target : targets) {
      var urls = new java.util.LinkedHashSet<String>();
      byAsset
          .getOrDefault(target.assetName(), Map.of())
          .forEach(
              (id, quote) -> {
                var selected = selectedReferences.get(target.assetName());
                if (selected != null && !selected.contains(id)) return;
                urls.add(quote.url());
                grouped
                    .computeIfAbsent(quote.url(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(id, quote);
              });
      var asset = new LinkedHashMap<String, Object>();
      asset.put("assetName", target.assetName());
      asset.put("sourceUrls", urls);
      if (riskSearchStatuses.containsKey(target.assetName()))
        asset.put("riskSearchStatus", riskSearchStatuses.get(target.assetName()));
      assets.add(asset);
    }
    var sources = new ArrayList<Map<String, Object>>();
    grouped.forEach(
        (url, quotes) -> {
          var document = documents.get(url);
          Map<String, Object> source = new LinkedHashMap<>();
          source.put("url", url);
          source.put("title", document.title());
          source.put(
              "publishedOn",
              document.publishedOn() == null ? null : document.publishedOn().toString());
          Map<String, String> numberedQuotes = new LinkedHashMap<>();
          Map<String, Map<String, String>> verifiedAmounts = new LinkedHashMap<>();
          var amountsByQuote =
              document.verifiedWonAmounts(quotes.values().stream().map(Quote::text).toList());
          quotes.forEach(
              (id, quote) -> {
                String number = referenceNumbers.get(id).toString();
                numberedQuotes.put(number, quote.text());
                var amounts = amountsByQuote.getOrDefault(quote.text(), Map.of());
                if (!amounts.isEmpty()) verifiedAmounts.put(number, amounts);
              });
          source.put("quotes", numberedQuotes);
          if (!verifiedAmounts.isEmpty()) source.put("verifiedWonAmounts", verifiedAmounts);
          sources.add(source);
        });
    return Map.of("assets", assets, "sources", sources);
  }

  List<String> references(List<PortfolioAnalysisTarget> targets) {
    return targets.stream()
        .flatMap(target -> byAsset.getOrDefault(target.assetName(), Map.of()).keySet().stream())
        .distinct()
        .toList();
  }

  List<Map<String, Object>> unavailableSources(List<PortfolioAnalysisTarget> targets) {
    return sourcesToReplace(
        targets.stream().filter(target -> references(List.of(target)).isEmpty()).toList());
  }

  List<Map<String, Object>> sourcesToReplace(List<PortfolioAnalysisTarget> targets) {
    return targets.stream()
        .map(
            target ->
                Map.<String, Object>of(
                    "assetName",
                    target.assetName(),
                    "unreadableUrls",
                    requestedUrls.getOrDefault(target.assetName(), List.of()),
                    "unavailableDomains",
                    unavailableDomains(target.assetName())))
        .toList();
  }

  private List<String> unavailableDomains(String assetName) {
    return requestedUrls.getOrDefault(assetName, List.of()).stream()
        .distinct()
        .collect(java.util.stream.Collectors.groupingBy(url -> java.net.URI.create(url).getHost()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue().size() >= 2)
        .filter(entry -> entry.getValue().stream().noneMatch(documents::containsKey))
        .map(Map.Entry::getKey)
        .toList();
  }

  int referenceCount() {
    return referenceIds.size();
  }

  String referenceKey(com.fasterxml.jackson.databind.JsonNode value) {
    if (!value.isIntegralNumber()) return value.asText();
    if (!value.canConvertToInt()) return "";
    int number = value.intValue();
    return number > 0 && number <= referenceIds.size() ? referenceIds.get(number - 1) : "";
  }

  boolean needsRecentSource(PortfolioAnalysisTarget target, java.time.LocalDate since) {
    if (references(List.of(target)).isEmpty()) return true;
    return latestPublication(target.assetName()).map(date -> date.isBefore(since)).orElse(false);
  }

  private java.util.Optional<java.time.LocalDate> latestPublication(String assetName) {
    return byAsset.getOrDefault(assetName, Map.of()).values().stream()
        .map(Quote::url)
        .map(documents::get)
        .map(PortfolioAnalysisSourceDocument::publishedOn)
        .filter(java.util.Objects::nonNull)
        .max(java.util.Comparator.naturalOrder());
  }

  PortfolioAnalysisEvidence mergeNewerSources(PortfolioAnalysisEvidence other) {
    Map<String, Map<String, Quote>> accepted = new LinkedHashMap<>();
    other.byAsset.forEach(
        (name, quotes) -> {
          var previous = latestPublication(name);
          var replacement = other.latestPublication(name);
          if (byAsset.getOrDefault(name, Map.of()).isEmpty()
              || (replacement.isPresent()
                  && (previous.isEmpty() || replacement.get().isAfter(previous.get())))) {
            accepted.put(name, quotes);
          }
        });
    return merge(new PortfolioAnalysisEvidence(accepted, other.documents, other.requestedUrls));
  }

  PortfolioAnalysisEvidence merge(PortfolioAnalysisEvidence other) {
    Map<String, Map<String, Quote>> merged = new LinkedHashMap<>(byAsset);
    other.byAsset.forEach(
        (name, quotes) -> {
          if (!quotes.isEmpty()) merged.put(name, quotes);
        });
    Map<String, PortfolioAnalysisSourceDocument> combined = new LinkedHashMap<>(documents);
    combined.putAll(other.documents);
    Map<String, List<String>> urls = new LinkedHashMap<>(requestedUrls);
    urls.putAll(other.requestedUrls);
    return new PortfolioAnalysisEvidence(merged, combined, urls);
  }

  /** 반대 근거는 날짜와 관계없이 함께 제공한다. 동일 본문의 재보도만 제거한다. */
  PortfolioAnalysisEvidence withRiskSources(PortfolioAnalysisEvidence risks) {
    Map<String, List<String>> urls = new LinkedHashMap<>(requestedUrls);
    Map<String, PortfolioAnalysisSourceDocument> combined = new LinkedHashMap<>(documents);
    combined.putAll(risks.documents);
    Map<String, List<String>> accepted = new LinkedHashMap<>();
    byAsset.forEach(
        (name, quotes) ->
            accepted.put(name, quotes.values().stream().map(Quote::url).distinct().toList()));
    Map<String, String> statuses = new LinkedHashMap<>();
    risks.requestedUrls.forEach(
        (name, candidates) -> {
          statuses.put(name, candidates.isEmpty() ? "NOT_FOUND" : "SOURCES_FOUND");
          var selected = new ArrayList<>(urls.getOrDefault(name, List.of()));
          var readable = new ArrayList<>(accepted.getOrDefault(name, List.of()));
          var bodies = new java.util.HashSet<String>();
          readable.stream()
              .map(combined::get)
              .filter(java.util.Objects::nonNull)
              .map(document -> document.body().strip().replaceAll("\\s+", " "))
              .forEach(bodies::add);
          for (String url : candidates) {
            var document = combined.get(url);
            if (document != null && bodies.add(document.body().strip().replaceAll("\\s+", " "))) {
              selected.add(url);
              readable.add(url);
            }
          }
          urls.put(name, selected);
          accepted.put(name, readable);
        });
    // 같은 URL을 다시 읽어 본문이 바뀌었으면 최신 본문 전체에서 인용문을 구성한다.
    var merged = from(accepted, combined);
    return new PortfolioAnalysisEvidence(merged.byAsset, combined, urls, statuses);
  }

  boolean hasUnreadableSources() {
    return requestedUrls.values().stream()
        .flatMap(List::stream)
        .anyMatch(url -> !documents.containsKey(url));
  }

  /** 원문을 다시 조회하지 않고 서버가 보관한 인용문과 날짜를 연결한다. */
  String resolve(String content, ObjectMapper mapper) throws java.io.IOException {
    var root = mapper.readTree(content);
    for (var impact : root.path("impacts")) {
      String asset = impact.path("assetName").asText();
      var amounts = new java.util.HashSet<String>();
      for (var evidence : impact.path("evidence")) {
        String reference = referenceKey(evidence.path("reference"));
        Quote quote = byAsset.getOrDefault(asset, Map.of()).get(reference);
        if (quote == null) continue;
        var document = documents.get(quote.url());
        amounts.addAll(document.billionWonAmounts(quote.text()));
        var node = (ObjectNode) evidence;
        node.remove("reference");
        node.put("url", quote.url());
        node.put("factQuote", quote.text());
        node.put("excerpt", quote.text());
        if (document.publishedOn() == null) node.putNull("publishedOn");
        else node.put("publishedOn", document.publishedOn().toString());
        var occurred = node.path("occurredOn");
        if (occurred.isTextual()) {
          try {
            var confirmed =
                document.confirmedOccurrence(
                    quote.text(), java.time.LocalDate.parse(occurred.asText()));
            if (confirmed == null) node.putNull("occurredOn");
          } catch (java.time.DateTimeException invalid) {
            // 확인할 수 없는 날짜만 비우고 원문 사실은 보존한다.
            node.putNull("occurredOn");
          }
        }
      }
      for (String field : List.of("issueSummary", "expectedReaction", "outlook", "reason")) {
        if (impact.path(field).isTextual())
          ((ObjectNode) impact)
              .put(
                  field,
                  PortfolioAnalysisSourceDocument.normalizeAmountUnits(
                      amounts, impact.path(field).asText()));
      }
    }
    return mapper.writeValueAsString(root);
  }

  List<PortfolioAnalysisResult.Source> sources() {
    return documents.entrySet().stream()
        .map(entry -> new PortfolioAnalysisResult.Source(entry.getValue().title(), entry.getKey()))
        .toList();
  }

  Map<String, PortfolioAnalysisSourceDocument> documents() {
    return documents;
  }

  private record Quote(String url, String text) {}
}
