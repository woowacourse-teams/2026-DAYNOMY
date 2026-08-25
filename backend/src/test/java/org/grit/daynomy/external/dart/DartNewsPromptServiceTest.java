package org.grit.daynomy.external.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseItem;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseResponse;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondItem;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondResponse;
import org.grit.daynomy.external.dart.dto.DartDisclosureItem;
import org.grit.daynomy.external.dart.dto.DartDisclosureResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DartNewsPromptServiceTest {

  @Test
  @DisplayName("공시명에 포함된 주요보고서 상세만 접수번호로 찾아 프롬프트에 붙인다")
  void createPromptsWithMajorReportDetails() {
    DartClient dartClient = Mockito.mock(DartClient.class);
    DartNewsPromptService service =
        new DartNewsPromptService(dartClient, new DartNewsPromptMapper());
    LocalDate beginDate = LocalDate.of(2026, 8, 1);
    LocalDate endDate = LocalDate.of(2026, 8, 17);
    DartDisclosureItem disclosure =
        new DartDisclosureItem(
            "K",
            "테스트",
            "00126380",
            "123456",
            "유상증자 및 전환사채 발행결정",
            "20260817000001",
            "테스트",
            "20260817",
            "");

    when(dartClient.getDisclosures(beginDate, endDate, "B", "K"))
        .thenReturn(new DartDisclosureResponse("000", "정상", List.of(disclosure)));
    when(dartClient.getCapitalIncreases("00126380", beginDate, endDate))
        .thenReturn(
            new DartCapitalIncreaseResponse(
                "000",
                "정상",
                List.of(
                    capitalIncrease("20260817000000", "무시"),
                    capitalIncrease("20260817000001", "주주배정증자"))));
    when(dartClient.getConvertibleBonds("00126380", beginDate, endDate))
        .thenReturn(
            new DartConvertibleBondResponse(
                "000", "정상", List.of(convertibleBond("20260817000001", "50000"))));

    String prompt = service.createPrompts(beginDate, endDate, "B", "K").getFirst().prompt();

    assertThat(prompt)
        .contains("경제 전문 기자", "JSON 형식으로만", "불릿, 번호 목록, 표", "정정 전후 변경 사항")
        .contains("[유상증자 상세]", "주주배정증자", "[전환사채권 발행결정 상세]", "50000")
        .doesNotContain("무시", "[회사합병 결정 상세]");
    verify(dartClient, never()).getMergerDecisions("00126380", beginDate, endDate);
  }

  @Test
  @DisplayName("원문 조회에 실패해도 공시 기본 정보로 프롬프트를 만든다")
  void createPromptsWithoutOriginalDocument() {
    DartClient dartClient = Mockito.mock(DartClient.class);
    DartNewsPromptService service =
        new DartNewsPromptService(dartClient, new DartNewsPromptMapper());
    LocalDate date = LocalDate.of(2026, 8, 24);
    DartDisclosureItem disclosure =
        new DartDisclosureItem(
            "Y",
            "SJG세종",
            "00134510",
            "033530",
            "[첨부정정]주요사항보고서(회사합병결정)",
            "20260824000096",
            "SJG세종",
            "20260824",
            "");

    when(dartClient.getDisclosures(date, date, "B", "Y"))
        .thenReturn(new DartDisclosureResponse("000", "정상", List.of(disclosure)));
    when(dartClient.getOriginalDocument("20260824000096"))
        .thenThrow(new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED));

    String prompt = service.createPrompts(date, date, "B", "Y").getFirst().prompt();

    assertThat(prompt)
        .contains("[DART 공시 정보]", "SJG세종", "20260824000096")
        .doesNotContain("[DART 원문 및 첨부문서]");
  }

  private DartCapitalIncreaseItem capitalIncrease(String receiptNo, String increaseMethod) {
    return new DartCapitalIncreaseItem(
        receiptNo,
        "K",
        "00126380",
        "테스트",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        increaseMethod);
  }

  private DartConvertibleBondItem convertibleBond(String receiptNo, String conversionPrice) {
    return new DartConvertibleBondItem(
        receiptNo,
        "K",
        "00126380",
        "테스트",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        conversionPrice,
        null,
        null,
        null,
        null,
        null);
  }
}
