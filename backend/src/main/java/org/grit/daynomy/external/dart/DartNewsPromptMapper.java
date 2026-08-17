package org.grit.daynomy.external.dart;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
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

  public NewsPrompt toPrompt(DartDisclosureItem disclosure) {
    return createPrompt(disclosure, disclosureBlock(disclosure));
  }

  public NewsPrompt toPrompt(DartDisclosureItem disclosure, List<String> detailBlocks) {
    String details = detailBlocks.isEmpty() ? "" : "\n" + String.join("\n", detailBlocks);
    return createPrompt(disclosure, disclosureBlock(disclosure) + details);
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
        다음 DART 공시 데이터를 기반으로 투자 참고용 뉴스를 작성해줘.
        제공된 데이터에 없는 수치, 원인, 결과는 만들지 마.
        주가 상승 또는 하락을 단정하지 마.
        결과는 title, description, content 필드를 만들 수 있게 작성해.
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
        + disclosure.flrNm()
        + "\n- 원문 링크: "
        + DART_DISCLOSURE_URL
        + disclosure.rceptNo();
  }

  private LocalDateTime parsePublishedAt(String date) {
    try {
      return LocalDate.parse(date, DART_DATE_FORMAT).atStartOfDay();
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.DART_NEWS_MAPPING_FAILED);
    }
  }

  private String formatDescriptionDate(String date) {
    LocalDate parsedDate = parsePublishedAt(date).toLocalDate();
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
