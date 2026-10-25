package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisEvidenceTest {
  @Test
  void numericReferencesResolveOnlyToTheirOriginalAndRejectOutOfRangeNumbers() throws Exception {
    var input = mapper.valueToTree(evidence.input(List.of(new PortfolioAnalysisTarget("삼성전자"))));
    int number = input.path("sources").get(0).path("quotes").get(0).path("reference").asInt();
    assertThat(number).isEqualTo(1);
    var raw =
        mapper.writeValueAsString(
            Map.of(
                "impacts",
                List.of(
                    Map.of(
                        "assetName",
                        "삼성전자",
                        "evidence",
                        List.of(Map.of("reference", number, "excerpt", "영업이익이 증가했어요."))))));
    assertThat(
            mapper
                .readTree(evidence.resolve(raw, mapper))
                .path("impacts")
                .get(0)
                .path("evidence")
                .get(0)
                .path("factQuote")
                .asText())
        .isEqualTo(quote);
    assertThat(evidence.referenceKey(mapper.readTree("0"))).isEmpty();
    assertThat(evidence.referenceKey(mapper.readTree("2147483648"))).isEmpty();
    assertThat(evidence.referenceKey(mapper.readTree("2"))).isEmpty();
  }

  private final ObjectMapper mapper = new ObjectMapper();
  private final String url = "https://example.com/results";
  private final String quote = "Operating profit increased by 10%.";
  private final PortfolioAnalysisEvidence evidence =
      PortfolioAnalysisEvidence.from(
          Map.of("삼성전자", List.of(url), "카카오", List.of()),
          Map.of(
              url,
              new PortfolioAnalysisSourceDocument("Results", quote, LocalDate.of(2026, 7, 30))));

  @Test
  void attachesOriginalQuoteAndPublicationWithoutTrustingAiFields() throws Exception {
    var reference = PortfolioAnalysisEvidence.reference(url, quote);
    var raw =
        mapper.writeValueAsString(
            Map.of(
                "impacts",
                List.of(
                    Map.of(
                        "assetName",
                        "삼성전자",
                        "evidence",
                        List.of(
                            Map.of(
                                "reference",
                                reference,
                                "occurredOn",
                                "2026-07-29",
                                "excerpt",
                                "영업이익이 증가했어요.",
                                "url",
                                "https://untrusted.example/fake",
                                "factQuote",
                                "Profit fell.",
                                "publishedOn",
                                "2999-01-01"))))));
    var resolved =
        mapper
            .readTree(evidence.resolve(raw, mapper))
            .path("impacts")
            .get(0)
            .path("evidence")
            .get(0);
    assertThat(resolved.path("url").asText()).isEqualTo(url);
    assertThat(resolved.path("factQuote").asText()).isEqualTo(quote);
    assertThat(resolved.path("publishedOn").asText()).isEqualTo("2026-07-30");
    assertThat(resolved.path("occurredOn").isNull()).isTrue();
  }

  @Test
  void doesNotAllowAnotherCompanyToUseItsReference() throws Exception {
    var raw =
        mapper.writeValueAsString(
            Map.of(
                "impacts",
                List.of(
                    Map.of(
                        "assetName",
                        "카카오",
                        "evidence",
                        List.of(
                            Map.of(
                                "reference",
                                PortfolioAnalysisEvidence.reference(url, quote),
                                "excerpt",
                                "영업이익이 증가했어요."))))));
    var unresolved =
        mapper
            .readTree(evidence.resolve(raw, mapper))
            .path("impacts")
            .get(0)
            .path("evidence")
            .get(0);
    assertThat(unresolved.has("reference")).isTrue();
    assertThat(unresolved.has("url")).isFalse();
  }

  @Test
  void suppliesCompleteSentencesAndStableReferences() {
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("삼성전자"))))
        .containsExactly(PortfolioAnalysisEvidence.reference(url, quote));
    assertThat(evidence.input(List.of(new PortfolioAnalysisTarget("삼성전자"))).toString())
        .contains(quote);
  }

  @Test
  void sharedOriginalIsSentOnceWithoutRemovingAnySentences() {
    var shared =
        PortfolioAnalysisEvidence.from(
            Map.of("삼성전자", List.of(url), "카카오", List.of(url)),
            Map.of(url, new PortfolioAnalysisSourceDocument("Results", quote, null)));
    var input =
        mapper.valueToTree(
            shared.input(
                List.of(new PortfolioAnalysisTarget("삼성전자"), new PortfolioAnalysisTarget("카카오"))));
    assertThat(input.path("assets")).hasSize(2);
    assertThat(input.path("sources")).hasSize(1);
    assertThat(input.path("sources").get(0).path("quotes")).hasSize(1);
    assertThat(input.path("sources").get(0).path("quotes").get(0).path("text").asText())
        .isEqualTo(quote);
  }

  @Test
  void correctionKeepsSelectedOriginalFactsWhileInitialInputKeepsAllFacts() {
    String other = "Operating costs increased by 3%.";
    var prepared =
        PortfolioAnalysisEvidence.from(
            Map.of("삼성전자", List.of(url)),
            Map.of(url, new PortfolioAnalysisSourceDocument("Results", quote + " " + other, null)));
    var targets = List.of(new PortfolioAnalysisTarget("삼성전자"));
    var correction =
        prepared.input(
            targets, Map.of("삼성전자", List.of(PortfolioAnalysisEvidence.reference(url, quote))));
    assertThat(correction.toString()).contains(quote).doesNotContain(other);
    assertThat(prepared.input(targets).toString()).contains(quote, other);
    assertThat(prepared.references(targets)).hasSize(2);
  }

  @Test
  void fillsMissingCompanyWithoutLosingPreviouslyPreparedOriginals() {
    String otherUrl = "https://example.com/kakao-results";
    var recovered =
        PortfolioAnalysisEvidence.from(
            Map.of("카카오", List.of(otherUrl)),
            Map.of(otherUrl, new PortfolioAnalysisSourceDocument("카카오 실적", "매출이 증가했어요.", null)));
    var merged = evidence.merge(recovered);
    assertThat(merged.references(List.of(new PortfolioAnalysisTarget("삼성전자"))))
        .containsExactlyElementsOf(
            evidence.references(List.of(new PortfolioAnalysisTarget("삼성전자"))));
    assertThat(merged.references(List.of(new PortfolioAnalysisTarget("카카오")))).hasSize(1);
    assertThat(merged.documents()).containsKeys(url, otherUrl);
  }

  @Test
  void appliesExplicitTableUnitToExplanationAndExcerptWithoutChangingOriginalQuote()
      throws Exception {
    String revenue = "Total Revenue 1,953 2,081 2,133 9% 3%";
    String profit = "Operating Profit 86 218 203 136% -7%";
    var prepared =
        PortfolioAnalysisEvidence.from(
            Map.of("카카오", List.of(url)),
            Map.of(
                url,
                new PortfolioAnalysisSourceDocument(
                    "Results", "(in bn KRW)\n" + revenue + "\n" + profit, null)));
    var raw =
        mapper.writeValueAsString(
            Map.of(
                "impacts",
                List.of(
                    Map.of(
                        "assetName",
                        "카카오",
                        "reason",
                        "매출 2,133억원과 영업이익 203억원을 보고했어요.",
                        "evidence",
                        List.of(
                            Map.of(
                                "reference",
                                PortfolioAnalysisEvidence.reference(url, revenue),
                                "excerpt",
                                "매출 2133억원이에요."),
                            Map.of(
                                "reference",
                                PortfolioAnalysisEvidence.reference(url, profit),
                                "excerpt",
                                "영업이익 203억원이에요."))))));
    var result = mapper.readTree(prepared.resolve(raw, mapper)).path("impacts").get(0);
    assertThat(result.path("reason").asText()).isEqualTo("매출 21330억원과 영업이익 2030억원을 보고했어요.");
    assertThat(result.path("evidence").get(0).path("excerpt").asText()).isEqualTo("매출 21330억원이에요.");
    assertThat(result.path("evidence").get(1).path("excerpt").asText())
        .isEqualTo("영업이익 2030억원이에요.");
    assertThat(result.path("evidence").get(1).path("factQuote").asText()).isEqualTo(profit);
  }
}
