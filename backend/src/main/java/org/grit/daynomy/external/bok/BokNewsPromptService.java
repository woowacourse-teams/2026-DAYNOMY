package org.grit.daynomy.external.bok;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.external.bok.dto.BokStatisticItem;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.NewsSource;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class BokNewsPromptService {

  private static final String BOK_URL = "https://ecos.bok.or.kr";

  private final BokClient bokClient;
  private final BokProperties bokProperties;

  public List<NewsPrompt> createPrompts() {
    return Optional.ofNullable(bokProperties.indicators()).orElse(List.of()).stream()
        .filter(this::hasRequiredValue)
        .map(this::createPrompt)
        .flatMap(Optional::stream)
        .toList();
  }

  private boolean hasRequiredValue(BokProperties.Indicator indicator) {
    if (indicator.statisticCode() == null
        || indicator.statisticCode().isBlank()
        || indicator.cycle() == null
        || indicator.cycle().isBlank()) {
      log.info("Skipping BOK indicator without statisticCode or cycle: key={}", indicator.key());
      return false;
    }
    return true;
  }

  private Optional<NewsPrompt> createPrompt(BokProperties.Indicator indicator) {
    List<BokStatisticItem> items =
        bokClient.getRecentData(indicator).stream()
            .filter(item -> number(item.value()).isPresent())
            .sorted(Comparator.comparing(BokStatisticItem::period))
            .toList();
    if (items.size() < 2) {
      return Optional.empty();
    }

    BokStatisticItem previous = items.get(items.size() - 2);
    BokStatisticItem latest = items.get(items.size() - 1);
    BigDecimal previousValue = number(previous.value()).orElseThrow();
    BigDecimal latestValue = number(latest.value()).orElseThrow();
    BigDecimal change = latestValue.subtract(previousValue);
    BigDecimal changeRate =
        previousValue.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : change
                .multiply(BigDecimal.valueOf(100))
                .divide(previousValue, 2, RoundingMode.HALF_UP);

    return Optional.of(
        new NewsPrompt(
            NewsSource.BOK,
            indicator.key() + ":" + latest.period(),
            BOK_URL,
            indicator.category(),
            LocalDateTime.now(),
            prompt(indicator, previous, latest, change, changeRate)));
  }

  private String prompt(
      BokProperties.Indicator indicator,
      BokStatisticItem previous,
      BokStatisticItem latest,
      BigDecimal change,
      BigDecimal changeRate) {
    return """
        다음 한국은행 ECOS 경제통계 데이터를 기반으로 투자 참고용 뉴스를 작성해줘.
        제공된 데이터에 없는 원인이나 전망은 만들지 마.
        통계 수치를 왜곡하지 말고, 출처를 본문 하단에 자연스럽게 포함해.
        결과는 title, description, content 필드를 만들 수 있게 작성해.

        [한국은행 ECOS 통계 정보]
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
        - 출처: 한국은행 ECOS(%s, %s), 조회일자 %s
        """
        .formatted(
            indicator.name(),
            value(latest.statisticName(), indicator.statisticName()),
            value(latest.itemName(), indicator.itemName()),
            value(latest.unitName(), "확인 필요"),
            previous.period(),
            previous.value(),
            latest.period(),
            latest.value(),
            change,
            changeRate,
            indicator.sourceOrganization(),
            value(latest.statisticName(), indicator.statisticName()),
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
