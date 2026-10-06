package org.grit.daynomy.external.openai;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAiNewsGenerator {

  private final OpenAiNewsResearcher researcher;
  private final OpenAiNewsWriter writer;

  public List<GeneratedEconomicNews> generateEconomicNews() {
    return writer.write(researcher.researchEconomicNews());
  }
}
