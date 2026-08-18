package org.grit.daynomy.keyword.ai;

import java.util.List;
import org.grit.daynomy.keyword.domain.Keyword;

public interface KeywordAiClient {

  List<Keyword> extractKeywords(String newsContent);
}
