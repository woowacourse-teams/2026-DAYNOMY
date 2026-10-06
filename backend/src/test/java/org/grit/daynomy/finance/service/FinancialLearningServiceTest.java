package org.grit.daynomy.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.finance.domain.LearningItemType;
import org.grit.daynomy.finance.domain.LearningProgress;
import org.grit.daynomy.finance.domain.SimulatedTrade;
import org.grit.daynomy.finance.domain.SimulatedTradeType;
import org.grit.daynomy.finance.domain.WeeklyCheckIn;
import org.grit.daynomy.finance.dto.FinancialLearningDto.CheckInRequest;
import org.grit.daynomy.finance.dto.FinancialLearningDto.MockTradeCreateRequest;
import org.grit.daynomy.finance.dto.FinancialLearningDto.ProgressUpdateRequest;
import org.grit.daynomy.finance.exception.FinancialLearningErrorCode;
import org.grit.daynomy.finance.repository.LearningProgressRepository;
import org.grit.daynomy.finance.repository.SimulatedTradeRepository;
import org.grit.daynomy.finance.repository.WeeklyCheckInRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialLearningServiceTest {

  @Mock private LearningProgressRepository progressRepository;
  @Mock private WeeklyCheckInRepository checkInRepository;
  @Mock private SimulatedTradeRepository mockTradeRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private AssetRepository assetRepository;
  @InjectMocks private FinancialLearningService service;

  @Test
  void updateProgressCreatesAndSavesMemberProgress() {
    Member member = mock(Member.class);
    given(progressRepository.findByMemberIdAndItemKey(1L, "guide-credit-card"))
        .willReturn(Optional.empty());
    given(memberRepository.findById(1L)).willReturn(Optional.of(member));
    given(progressRepository.save(any(LearningProgress.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.updateProgress(
            1L, "guide-credit-card", new ProgressUpdateRequest(LearningItemType.GUIDE, true, true));

    assertThat(response.itemKey()).isEqualTo("guide-credit-card");
    assertThat(response.completed()).isTrue();
    assertThat(response.bookmarked()).isTrue();
  }

  @Test
  void saveCheckInUpdatesTheSameWeekInsteadOfCreatingAnotherRecord() {
    WeeklyCheckIn checkIn = WeeklyCheckIn.create(mock(Member.class), LocalDate.of(2026, 9, 28));
    given(checkInRepository.findByMemberIdAndWeekStart(1L, LocalDate.of(2026, 9, 28)))
        .willReturn(Optional.of(checkIn));
    given(checkInRepository.save(checkIn)).willReturn(checkIn);

    var response =
        service.saveCheckIn(
            1L,
            LocalDate.of(2026, 9, 28),
            new CheckInRequest(
                amount("100000"),
                amount("50000"),
                amount("0"),
                amount("90000"),
                amount("50000"),
                amount("0"),
                "  이번 주 지출을 줄였다  "));

    assertThat(response.actualSavings()).isEqualByComparingTo("90000");
    assertThat(response.note()).isEqualTo("이번 주 지출을 줄였다");
    then(memberRepository).shouldHaveNoInteractions();
  }

  @Test
  void checkInWeekMustStartOnMonday() {
    CheckInRequest request =
        new CheckInRequest(
            amount("0"), amount("0"), amount("0"), amount("0"), amount("0"), amount("0"), null);

    assertThatThrownBy(() -> service.saveCheckIn(1L, LocalDate.of(2026, 10, 3), request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(FinancialLearningErrorCode.INVALID_CHECK_IN_WEEK_START));
  }

  @Test
  void sellCannotExceedCurrentMockQuantity() {
    Member member = mock(Member.class);
    Asset asset = asset();
    SimulatedTrade buy =
        new SimulatedTrade(
            member,
            asset,
            SimulatedTradeType.BUY,
            2,
            amount("70000"),
            "첫 연습",
            LocalDate.of(2026, 10, 1));
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(assetRepository.findById(2L)).willReturn(Optional.of(asset));
    given(mockTradeRepository.findAllByMemberIdOrderByTradedOnDescIdDesc(1L))
        .willReturn(List.of(buy));

    assertThatThrownBy(
            () ->
                service.addMockTrade(
                    1L,
                    new MockTradeCreateRequest(
                        2L,
                        SimulatedTradeType.SELL,
                        3,
                        amount("75000"),
                        "익절 연습",
                        LocalDate.of(2026, 10, 3))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(FinancialLearningErrorCode.MOCK_SELL_QUANTITY_EXCEEDED));

    then(mockTradeRepository).should().findAllByMemberIdOrderByTradedOnDescIdDesc(1L);
  }

  @Test
  void buyCannotBeDeletedWhenLaterSellWouldMakeQuantityNegative() {
    Member member = mock(Member.class);
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(2L);
    SimulatedTrade buy =
        new SimulatedTrade(
            member,
            asset,
            SimulatedTradeType.BUY,
            2,
            amount("70000"),
            null,
            LocalDate.of(2026, 10, 1));
    SimulatedTrade sell =
        new SimulatedTrade(
            member,
            asset,
            SimulatedTradeType.SELL,
            2,
            amount("75000"),
            null,
            LocalDate.of(2026, 10, 2));
    given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(mockTradeRepository.findByIdAndMemberId(10L, 1L)).willReturn(Optional.of(buy));
    given(mockTradeRepository.findAllByMemberIdOrderByTradedOnDescIdDesc(1L))
        .willReturn(List.of(sell, buy));

    assertThatThrownBy(() -> service.removeMockTrade(1L, 10L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(FinancialLearningErrorCode.MOCK_TRADE_DELETE_INVALID));
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }

  private Asset asset() {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(2L);
    given(asset.isListed()).willReturn(true);
    given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    return asset;
  }
}
