package org.grit.daynomy.external.kosis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.external.kosis.dto.KosisDataItem;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.NewsSource;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KosisNewsPromptService {

  private static final String KOSIS_URL = "https://kosis.kr";
  private static final List<String> BLOCKED_VIEW_CODES = List.of("MT_RTITLE", "MT_BUKHAN");

  private final KosisClient kosisClient;
  private final KosisProperties kosisProperties;

  public List<NewsPrompt> createPrompts() {
    List<KosisProperties.Indicator> indicators =
        Optional.ofNullable(kosisProperties.indicators()).orElse(List.of());
    log.info("Starting KOSIS prompt creation: indicatorCount={}", indicators.size());

    List<NewsPrompt> prompts =
        indicators.stream()
            .filter(this::isAllowed)
            .map(this::createPrompt)
            .flatMap(Optional::stream)
            .toList();
    log.info("Finished KOSIS prompt creation: promptCount={}", prompts.size());
    return prompts;
  }

  private boolean isAllowed(KosisProperties.Indicator indicator) {
    if (indicator.userStatsId() == null || indicator.userStatsId().isBlank()) {
      log.info("Skipping KOSIS indicator without userStatsId: key={}", indicator.key());
      return false;
    }

    boolean allowed = !BLOCKED_VIEW_CODES.contains(indicator.viewCode());
    if (!allowed) {
      log.warn(
          "Skipping KOSIS indicator with restricted viewCode: key={}, viewCode={}",
          indicator.key(),
          indicator.viewCode());
    }
    return allowed;
  }

  private Optional<NewsPrompt> createPrompt(KosisProperties.Indicator indicator) {
    log.info(
        "Creating KOSIS prompt for indicator: key={}, name={}, category={}",
        indicator.key(),
        indicator.name(),
        indicator.category());
    List<KosisDataItem> items =
        kosisClient.getRecentData(indicator).stream()
            .filter(item -> number(item.value()).isPresent())
            .sorted(Comparator.comparing(KosisDataItem::period))
            .toList();
    if (items.size() < 2) {
      log.info(
          "Skipping KOSIS prompt due to insufficient numeric data: key={}, numericItemCount={}",
          indicator.key(),
          items.size());
      return Optional.empty();
    }

    KosisDataItem previous = items.get(items.size() - 2);
    KosisDataItem latest = items.get(items.size() - 1);
    BigDecimal previousValue = number(previous.value()).orElseThrow();
    BigDecimal latestValue = number(latest.value()).orElseThrow();
    BigDecimal change = latestValue.subtract(previousValue);
    BigDecimal changeRate =
        previousValue.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : change
                .multiply(BigDecimal.valueOf(100))
                .divide(previousValue, 2, RoundingMode.HALF_UP);
    log.info(
        "Created KOSIS prompt data: key={}, previousPeriod={}, previousValue={}, latestPeriod={}, latestValue={}, change={}, changeRate={}%",
        indicator.key(),
        previous.period(),
        previous.value(),
        latest.period(),
        latest.value(),
        change,
        changeRate);

    return Optional.of(
        new NewsPrompt(
            NewsSource.KOSIS,
            indicator.key() + ":" + latest.period(),
            KOSIS_URL,
            indicator.category(),
            LocalDateTime.now(),
            prompt(indicator, previous, latest, change, changeRate)));
  }

  private String prompt(
      KosisProperties.Indicator indicator,
      KosisDataItem previous,
      KosisDataItem latest,
      BigDecimal change,
      BigDecimal changeRate) {
    return """
        다음 KOSIS 국내통계 데이터를 기반으로 투자 참고용 뉴스를 작성해줘.
        제공된 데이터에 없는 원인이나 전망은 만들지 마.
        통계 수치를 왜곡하지 말고, 출처를 본문 하단에 자연스럽게 포함해.
        결과는 title, description, content 필드를 만들 수 있게 작성해.

        [KOSIS 통계 정보]
        - 지표명: %s
        - 통계표명: %s
        - 항목명: %s
        - 단위: %s
        - 이전 시점: %s
        - 이전 값: %s
        - 최신 시점: %s
        - 최신 값: %s
        - 증감: %s
        - 증감률: %s%%
        - 출처: KOSIS(%s, %s, %s), 조회일자 %s
        """
        .formatted(
            indicator.name(),
            value(latest.tableName(), indicator.tableName()),
            value(latest.itemName(), indicator.name()),
            value(latest.unitName(), "확인 필요"),
            previous.period(),
            previous.value(),
            latest.period(),
            latest.value(),
            change,
            changeRate,
            indicator.sourceOrganization(),
            indicator.surveyName(),
            indicator.tableName(),
            LocalDateTime.now().toLocalDate());
  }

  private Optional<BigDecimal> number(String value) {
    try {
      return value == null || value.isBlank()
          ? Optional.empty()
          : Optional.of(new BigDecimal(value.replace(",", "")));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
