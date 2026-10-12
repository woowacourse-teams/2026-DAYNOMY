package org.grit.daynomy.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.feedback.domain.Feedback;
import org.grit.daynomy.feedback.domain.FeedbackStatus;
import org.grit.daynomy.feedback.dto.FeedbackCreateRequest;
import org.grit.daynomy.feedback.dto.FeedbackStatusUpdateRequest;
import org.grit.daynomy.feedback.exception.FeedbackErrorCode;
import org.grit.daynomy.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

  @Mock private FeedbackRepository feedbackRepository;
  @InjectMocks private FeedbackService feedbackService;

  @Test
  @DisplayName("피드백 등록은 내용과 작성 화면의 앞뒤 공백을 제거해 저장한다")
  void createStripsInput() {
    feedbackService.create(new FeedbackCreateRequest("  화면이 좋아요.  ", "  /news/1  "));

    ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
    then(feedbackRepository).should().save(captor.capture());
    assertThat(captor.getValue().getContent()).isEqualTo("화면이 좋아요.");
    assertThat(captor.getValue().getPagePath()).isEqualTo("/news/1");
    assertThat(captor.getValue().getStatus()).isEqualTo(FeedbackStatus.PENDING);
  }

  @Test
  @DisplayName("관리자는 피드백 상태를 처리완료로 변경한다")
  void updateStatus() {
    Feedback feedback = Feedback.create("화면이 좋아요.", "/news/1");
    given(feedbackRepository.findById(1L)).willReturn(Optional.of(feedback));

    var response =
        feedbackService.updateStatus(1L, new FeedbackStatusUpdateRequest(FeedbackStatus.RESOLVED));

    assertThat(feedback.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
    assertThat(response.status()).isEqualTo(FeedbackStatus.RESOLVED);
  }

  @Test
  @DisplayName("없는 피드백 조회는 도메인 오류를 반환한다")
  void getDetailRejectsMissingFeedback() {
    given(feedbackRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> feedbackService.getDetail(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(FeedbackErrorCode.FEEDBACK_NOT_FOUND);
  }
}
