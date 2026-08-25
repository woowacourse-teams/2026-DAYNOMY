package org.grit.daynomy.external.dart;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseItem;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseResponse;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondItem;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondResponse;
import org.grit.daynomy.external.dart.dto.DartDisclosureItem;
import org.grit.daynomy.external.dart.dto.DartDisclosureResponse;
import org.grit.daynomy.external.dart.dto.DartMergerDecisionItem;
import org.grit.daynomy.external.dart.dto.DartMergerDecisionResponse;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class DartNewsPromptService {

  private final DartClient dartClient;
  private final DartNewsPromptMapper dartNewsPromptMapper;

  public List<NewsPrompt> createPrompts(
      LocalDate beginDate, LocalDate endDate, String disclosureType, String corporationClass) {
    DartDisclosureResponse response =
        dartClient.getDisclosures(beginDate, endDate, disclosureType, corporationClass);
    if (response == null || response.list() == null) {
      return List.of();
    }

    return response.list().stream()
        .map(disclosure -> createPrompt(disclosure, beginDate, endDate))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<NewsPrompt> createPrompt(
      DartDisclosureItem disclosure, LocalDate beginDate, LocalDate endDate) {
    String originalDocument = getOriginalDocumentOrBlank(disclosure);
    List<DartMajorReportType> types = DartMajorReportType.fromAll(disclosure.reportNm());
    if (types.isEmpty()) {
      log.info(
          "Skipping DART prompt due to unsupported report type: receiptNo={}, reportName={}",
          disclosure.rceptNo(),
          disclosure.reportNm());
      return Optional.empty();
    }

    List<String> detailBlocks =
        types.stream()
            .map(type -> findDetailBlock(type, disclosure, beginDate, endDate))
            .flatMap(Optional::stream)
            .toList();
    if (detailBlocks.isEmpty()) {
      log.info(
          "Skipping DART prompt due to insufficient report details: receiptNo={}, reportName={}",
          disclosure.rceptNo(),
          disclosure.reportNm());
      return Optional.empty();
    }

    return Optional.of(dartNewsPromptMapper.toPrompt(disclosure, detailBlocks, originalDocument));
  }

  private String getOriginalDocumentOrBlank(DartDisclosureItem disclosure) {
    try {
      return dartClient.getOriginalDocument(disclosure.rceptNo());
    } catch (BusinessException exception) {
      log.warn(
          "Skipping DART original document: receiptNo={}, reportName={}",
          disclosure.rceptNo(),
          disclosure.reportNm());
      return "";
    }
  }

  private Optional<String> findDetailBlock(
      DartMajorReportType type,
      DartDisclosureItem disclosure,
      LocalDate beginDate,
      LocalDate endDate) {
    return switch (type) {
      case CAPITAL_INCREASE ->
          findByReceiptNo(
                  disclosure.rceptNo(),
                  listOf(
                      dartClient.getCapitalIncreases(disclosure.corpCode(), beginDate, endDate),
                      DartCapitalIncreaseResponse::list),
                  DartCapitalIncreaseItem::rceptNo)
              .map(dartNewsPromptMapper::toDetailBlock);
      case CONVERTIBLE_BOND ->
          findByReceiptNo(
                  disclosure.rceptNo(),
                  listOf(
                      dartClient.getConvertibleBonds(disclosure.corpCode(), beginDate, endDate),
                      DartConvertibleBondResponse::list),
                  DartConvertibleBondItem::rceptNo)
              .map(dartNewsPromptMapper::toDetailBlock);
      case MERGER ->
          findByReceiptNo(
                  disclosure.rceptNo(),
                  listOf(
                      dartClient.getMergerDecisions(disclosure.corpCode(), beginDate, endDate),
                      DartMergerDecisionResponse::list),
                  DartMergerDecisionItem::rceptNo)
              .map(dartNewsPromptMapper::toDetailBlock);
    };
  }

  private <T> Optional<T> findByReceiptNo(
      String receiptNo, List<T> details, Function<T, String> receiptNoGetter) {
    return details.stream()
        .filter(detail -> receiptNo.equals(receiptNoGetter.apply(detail)))
        .findFirst();
  }

  private <R, T> List<T> listOf(R response, Function<R, List<T>> listGetter) {
    if (response == null) {
      return List.of();
    }

    return Optional.ofNullable(listGetter.apply(response)).orElse(List.of());
  }
}
