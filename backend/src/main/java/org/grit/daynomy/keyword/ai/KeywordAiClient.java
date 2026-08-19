package org.grit.daynomy.keyword.ai;

import java.util.List;
import org.grit.daynomy.keyword.domain.NewsKeyword;

public interface KeywordAiClient {

  List<NewsKeyword> extractKeywords(String newsContent);
}
