package org.grit.daynomy.external.openai;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Industry;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.ai.NewsGenerationTopicResearch;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAiNewsGenerator {

  private final OpenAiNewsResearcher researcher;
  private final OpenAiNewsWriter writer;

  public List<EconomicNewsResearch> researchEconomicNews() {
    return researcher.researchEconomicNews();
  }

  public List<EconomicNewsResearch> researchUsEconomicNews() {
    return researcher.researchUsEconomicNews();
  }

  public List<GeneratedEconomicNews> write(List<NewsGenerationTopicResearch> research) {
    return writer.write(research);
  }

  public List<GeneratedEconomicNews> write(
      List<NewsGenerationTopicResearch> research, List<Industry> industries) {
    return writer.write(research, industries);
  }

  public List<GeneratedEconomicNews> generateEconomicNewsByKeyword(String keyword) {
    EconomicNewsResearch research = researcher.researchEconomicNews(keyword);
    return writer.write(
        List.of(
            new NewsGenerationTopicResearch(
                UUID.randomUUID().toString(),
                research.title(),
                research.referenceDate(),
                research.category(),
                research.facts(),
                research.sources())));
  }

  public List<GeneratedEconomicNews> generateEconomicNewsByKeyword(
      String keyword, List<Industry> industries) {
    EconomicNewsResearch research = researcher.researchEconomicNews(keyword);
    return writer.write(
        List.of(
            new NewsGenerationTopicResearch(
                UUID.randomUUID().toString(),
                research.title(),
                research.referenceDate(),
                research.category(),
                research.facts(),
                research.sources())),
        industries);
  }
}
