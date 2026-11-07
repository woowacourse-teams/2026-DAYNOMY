package org.grit.daynomy.external.openai;

import java.util.List;
import lombok.RequiredArgsConstructor;
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
}
