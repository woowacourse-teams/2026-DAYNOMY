package org.grit.daynomy.external.dart;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseItem;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondItem;
import org.grit.daynomy.external.dart.dto.DartDisclosureItem;
import org.grit.daynomy.external.dart.dto.DartMergerDecisionItem;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;
import org.springframework.stereotype.Component;

@Component
public class DartNewsPromptMapper {

  private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String DART_DISCLOSURE_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

  public NewsPrompt toPrompt(
      DartDisclosureItem disclosure, List<String> detailBlocks, String originalDocument) {
    String details = detailBlocks.isEmpty() ? "" : "\n" + String.join("\n", detailBlocks);
    String original =
        originalDocument == null || originalDocument.isBlank()
            ? ""
            : "\n\n[DART 원문 및 첨부문서]\n" + originalDocument;
    return createPrompt(disclosure, disclosureBlock(disclosure) + details + original);
  }

  String toDetailBlock(DartCapitalIncreaseItem detail) {
    return "[유상증자 상세]"
        + "\n- 신주 발행 수(보통주식): "
        + value(detail.newCommonStockCount())
        + "\n- 증자 전 발행주식 수(보통주식): "
        + value(detail.beforeCommonStockCount())
        + "\n- 증자방식: "
        + value(detail.increaseMethod())
        + "\n- 시설자금: "
        + value(detail.facilityFund())
        + "\n- 운영자금: "
        + value(detail.operatingFund())
        + "\n- 채무상환자금: "
        + value(detail.debtRepaymentFund())
        + "\n- 타법인 증권 취득자금: "
        + value(detail.otherCompanySecuritiesFund())
        + "\n- 기타자금: "
        + value(detail.otherFund());
  }

  String toDetailBlock(DartConvertibleBondItem detail) {
    return "[전환사채권 발행결정 상세]"
        + "\n- 사채 권면총액: "
        + value(detail.bondTotalAmount())
        + "\n- 자금조달 목적(시설자금): "
        + value(detail.facilityFund())
        + "\n- 자금조달 목적(운영자금): "
        + value(detail.operatingFund())
        + "\n- 자금조달 목적(채무상환자금): "
        + value(detail.debtRepaymentFund())
        + "\n- 표면이자율: "
        + value(detail.surfaceInterestRate())
        + "\n- 만기이자율: "
        + value(detail.maturityInterestRate())
        + "\n- 만기일: "
        + value(detail.maturityDate())
        + "\n- 전환가액: "
        + value(detail.conversionPrice())
        + "\n- 전환비율: "
        + value(detail.conversionRate())
        + "\n- 전환 가능 주식 수: "
        + value(detail.conversionStockCount())
        + "\n- 주식총수 대비 비율: "
        + value(detail.conversionStockRatio());
  }

  String toDetailBlock(DartMergerDecisionItem detail) {
    return "[회사합병 결정 상세]"
        + "\n- 합병상대회사: "
        + value(detail.counterpartyCompanyName())
        + "\n- 합병상대회사 주요사업: "
        + value(detail.counterpartyMainBusiness())
        + "\n- 합병방법: "
        + value(detail.mergerMethod())
        + "\n- 합병형태: "
        + value(detail.mergerType())
        + "\n- 합병목적: "
        + value(detail.mergerPurpose())
        + "\n- 합병비율: "
        + value(detail.mergerRatio())
        + "\n- 합병기일: "
        + value(detail.mergerDate());
  }

  private NewsPrompt createPrompt(DartDisclosureItem disclosure, String data) {
    return new NewsPrompt(
        NewsSource.DART,
        disclosure.rceptNo(),
        DART_DISCLOSURE_URL + disclosure.rceptNo(),
        Category.STOCK,
        parsePublishedAt(disclosure.rceptDt()),
        instruction() + "\n\n" + data);
  }

  private String instruction() {
    return """
        당신은 DART 전자공시 데이터를 바탕으로 기사를 작성하는 경제 전문 기자입니다.
        아래 지침을 따라 실제 뉴스 기사 형태로 작성하세요.

        [출력 형식]
        다음 JSON 형식으로만 출력하세요. 다른 텍스트나 설명은 포함하지 마세요.
        {
          "title": "...",
          "description": "...",
          "content": "..."
        }

        [title]
        핵심 변화가 드러나는 기사 제목으로 작성하세요.
        과장하거나 단정적인 표현은 쓰지 마세요.

        [description]
        기사 리드문처럼 한 문장으로 작성하세요.
        title을 그대로 반복하지 말고 핵심 정보를 보완하세요.

        [content]
        불릿, 번호 목록, 표, '요약:' 표현 없이 문단 형태로만 작성하세요.
        정보량에 따라 2~5개 문단으로 구성하고, 정보가 적은 공시는 억지로 늘리지 마세요.
        첫 문단에는 회사명, 공시일, 핵심 결정을 자연스러운 문장으로 쓰세요.
        정정공시는 정정 전후 변경 사항을 첫 문단 또는 두 번째 문단에서 명확히 대조하세요.
        여러 안건이 포함된 경우 공시에 명시된 금액, 지분율, 일정, 정정 항목을 기준으로 핵심 사안을 먼저 다루세요.
        "DART 공시에 따르면" 또는 "전자공시시스템에 공시된 내용에 따르면"처럼 출처를 본문에 명시하세요.
        접수번호, DART 회사 코드, 법인구분 등 식별 코드성 정보는 본문에 쓰지 마세요.

        [숫자 표기]
        금액은 원문 수치를 왜곡하지 마세요.
        원 단위 금액이 명확한 경우에만 억 원 또는 조 원 단위를 자연스럽게 병기하세요.
        백만원, 천원 등 원문 단위가 이미 축약된 경우 임의로 재계산하지 말고 원문 단위를 유지하세요.
        비율은 원문보다 더 정밀하게 쓰지 말고, 필요한 경우 소수점 첫째 자리까지만 쓰세요.

        [금지 사항]
        제공된 데이터에 없는 수치, 원인, 결과, 배경을 임의로 생성하지 마세요.
        주가나 시장 반응의 상승/하락을 단정하지 마세요.
        확인되지 않은 인과관계는 쓰지 마세요.
        투자 판단, 매수·매도 권유, 전망 단정 표현은 쓰지 마세요.

        결과는 반드시 한국어로 작성하세요.
        """;
  }

  private String disclosureBlock(DartDisclosureItem disclosure) {
    return "[DART 공시 정보]"
        + "\n- 회사명: "
        + disclosure.corpName()
        + "\n- 법인구분: "
        + disclosure.corpCls()
        + "\n- DART 회사 코드: "
        + disclosure.corpCode()
        + "\n- 종목 코드: "
        + value(disclosure.stockCode())
        + "\n- 공시명: "
        + disclosure.reportNm()
        + "\n- 접수번호: "
        + disclosure.rceptNo()
        + "\n- 접수일: "
        + formatDescriptionDate(disclosure.rceptDt())
        + "\n- 제출인: "
        + disclosure.flrNm();
  }

  private Instant parsePublishedAt(String date) {
    return parseDate(date).atStartOfDay(ZoneId.systemDefault()).toInstant();
  }

  private LocalDate parseDate(String date) {
    try {
      return LocalDate.parse(date, DART_DATE_FORMAT);
    } catch (RuntimeException exception) {
      throw new BusinessException(ExternalErrorCode.DART_NEWS_MAPPING_FAILED);
    }
  }

  private String formatDescriptionDate(String date) {
    LocalDate parsedDate = parseDate(date);
    return parsedDate.getYear()
        + "년 "
        + parsedDate.getMonthValue()
        + "월 "
        + parsedDate.getDayOfMonth()
        + "일";
  }

  private String value(String value) {
    return value == null || value.isBlank() ? "확인 필요" : value;
  }
}
