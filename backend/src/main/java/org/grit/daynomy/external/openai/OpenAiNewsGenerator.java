package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.NewsSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiNewsGenerator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern BULLET_LINE_PATTERN =
      Pattern.compile("(?m)^\\s*(?:[-*•·]|\\d+[.)])\\s+");
  private static final Pattern IDENTIFIER_PATTERN =
      Pattern.compile(
          "(?i)(접수번호|DART\\s*회사\\s*코드|회사\\s*코드|법인구분|종목\\s*코드|corp[_ ]?code|rcept[_ ]?no|stock[_ ]?code)");
  private static final Pattern SOURCE_ATTRIBUTION_PATTERN =
      Pattern.compile("(DART\\s*공시|전자공시시스템|공시에 따르면|공시된 내용에 따르면|공시에는)");
  private static final Pattern AWKWARD_ATTRIBUTION_PATTERN =
      Pattern.compile("(?s)(?:에 따르면|따르면)[^.!?。！？\\n]{0,40}(?:밝혔다|전했다)");
  private static final List<String> FORBIDDEN_PHRASES =
      List.of("요약:", "매수", "매도", "투자 권유", "급등", "급락", "주가 상승", "주가 하락");

  private final OpenAiProperties openAiProperties;
  private final RestClient restClient;

  public OpenAiNewsGenerator(OpenAiProperties openAiProperties) {
    this.openAiProperties = openAiProperties;
    this.restClient = RestClient.create(openAiProperties.baseUrl());
  }

  public GeneratedNews generate(NewsPrompt prompt) {
    try {
      log.info(
          "Requesting OpenAI news generation: model={}, source={}, externalId={}",
          openAiProperties.model(),
          prompt.source(),
          prompt.externalId());
      GeneratedNews generatedNews = requestNews(prompt, "");
      if (shouldValidate(prompt)) {
        ValidationResult validation = validateDartNews(generatedNews);
        if (!validation.valid()) {
          log.warn(
              "Generated DART news failed content validation: source={}, externalId={}, violations={}",
              prompt.source(),
              prompt.externalId(),
              validation.violations());
          generatedNews = requestNews(prompt, validation.correctionInstruction());
          ValidationResult retryValidation = validateDartNews(generatedNews);
          if (!retryValidation.valid()) {
            log.warn(
                "Regenerated DART news failed content validation: source={}, externalId={}, violations={}",
                prompt.source(),
                prompt.externalId(),
                retryValidation.violations());
            throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
          }
        }
      }
      log.info(
          "Received OpenAI generated news: source={}, externalId={}, title={}",
          prompt.source(),
          prompt.externalId(),
          generatedNews.title());
      return generatedNews;
    } catch (HttpStatusCodeException exception) {
      log.warn(
          "OpenAI news generation request failed: status={}, body={}, source={}, externalId={}",
          exception.getStatusCode(),
          exception.getResponseBodyAsString(),
          prompt.source(),
          prompt.externalId());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.warn(
          "OpenAI news generation request failed: message={}, source={}, externalId={}",
          exception.getMessage(),
          prompt.source(),
          prompt.externalId());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private GeneratedNews requestNews(NewsPrompt prompt, String correctionInstruction) {
    String response =
        restClient
            .post()
            .uri("/responses")
            .header("Authorization", "Bearer " + openAiProperties.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody(prompt, correctionInstruction))
            .retrieve()
            .body(String.class);
    return parseGeneratedNews(response);
  }

  private Map<String, Object> requestBody(NewsPrompt prompt, String correctionInstruction) {
    return Map.of(
        "model",
        openAiProperties.model(),
        "input",
        input(prompt, correctionInstruction),
        "text",
        Map.of("format", responseFormat()));
  }

  private Object input(NewsPrompt prompt, String correctionInstruction) {
    if (!prompt.hasStructuredInput()) {
      return prompt.prompt();
    }

    return List.of(
        Map.of("role", "developer", "content", prompt.instruction() + correctionInstruction),
        Map.of("role", "user", "content", prompt.sourceData()));
  }

  private boolean shouldValidate(NewsPrompt prompt) {
    return prompt.source() == NewsSource.DART && prompt.hasStructuredInput();
  }

  private ValidationResult validateDartNews(GeneratedNews generatedNews) {
    List<String> violations = new ArrayList<>();
    String title = value(generatedNews.title());
    String description = value(generatedNews.description());
    String content = value(generatedNews.content());
    String allText = title + "\n" + description + "\n" + content;

    if (title.isBlank()) {
      violations.add("title은 비어 있지 않아야 함");
    }
    if (description.isBlank()) {
      violations.add("description은 비어 있지 않아야 함");
    }
    if (content.isBlank()) {
      violations.add("content는 비어 있지 않아야 함");
    } else {
      int paragraphCount = paragraphCount(content);
      if (paragraphCount < 2 || paragraphCount > 5) {
        violations.add("content는 2~5개 문단이어야 함");
      }
      if (BULLET_LINE_PATTERN.matcher(content).find()) {
        violations.add("content에 불릿 또는 목록 형식이 없어야 함");
      }
      int sourceAttributionCount = countMatches(SOURCE_ATTRIBUTION_PATTERN, content);
      if (sourceAttributionCount != 1) {
        violations.add("content에 DART 출처 표현이 한 번만 있어야 함");
      }
      if (AWKWARD_ATTRIBUTION_PATTERN.matcher(content).find()) {
        violations.add("출처 표현과 전달 동사를 중복해서 쓰지 않아야 함");
      }
    }
    if (IDENTIFIER_PATTERN.matcher(allText).find()) {
      violations.add("식별 코드성 정보가 없어야 함");
    }
    FORBIDDEN_PHRASES.stream()
        .filter(allText::contains)
        .forEach(phrase -> violations.add("금지 표현이 없어야 함: " + phrase));

    return new ValidationResult(List.copyOf(violations));
  }

  private int paragraphCount(String content) {
    return (int)
        Arrays.stream(content.strip().split("\\R\\s*\\R"))
            .map(String::strip)
            .filter(paragraph -> !paragraph.isBlank())
            .count();
  }

  private int countMatches(Pattern pattern, String text) {
    int count = 0;
    var matcher = pattern.matcher(text);
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private String value(String text) {
    return text == null ? "" : text.strip();
  }

  private record ValidationResult(List<String> violations) {

    private boolean valid() {
      return violations.isEmpty();
    }

    private String correctionInstruction() {
      return "\n\n[재작성 지침]\n"
          + "이전 출력이 다음 검수 항목을 위반했습니다: "
          + String.join(", ", violations)
          + ". 위반 사항을 모두 수정한 기사만 출력하세요. "
          + "content는 반드시 2~5개 문단으로 작성하고 문단 사이에는 \\n\\n을 사용하세요. "
          + "출처 표현은 본문에 한 번만 넣고 '따르면 밝혔다'처럼 중복하지 마세요. "
          + "정보가 부족해도 사실을 반복하거나 추측하지 마세요. 참고 데이터에 없는 사실은 추가하지 마세요.";
    }
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "news_article",
        "strict",
        true,
        "schema",
        Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "properties",
            Map.of(
                "title",
                Map.of("type", "string"),
                "description",
                Map.of("type", "string"),
                "content",
                Map.of("type", "string")),
            "required",
            List.of("title", "description", "content")));
  }

  private GeneratedNews parseGeneratedNews(String response) {
    try {
      String outputText = extractOutputText(OBJECT_MAPPER.readTree(response));
      return OBJECT_MAPPER.readValue(outputText, GeneratedNews.class);
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private String extractOutputText(JsonNode response) {
    JsonNode output = response.path("output");
    for (JsonNode item : output) {
      for (JsonNode content : item.path("content")) {
        if ("output_text".equals(content.path("type").asText())) {
          String text = content.path("text").asText();
          if (!text.isBlank()) {
            return text;
          }
        }
      }
    }

    throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
  }
}
