package org.grit.daynomy.feedback.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.feedback.domain.Feedback;
import org.grit.daynomy.feedback.dto.AdminFeedbackPageResponse;
import org.grit.daynomy.feedback.dto.AdminFeedbackResponse;
import org.grit.daynomy.feedback.dto.FeedbackCreateRequest;
import org.grit.daynomy.feedback.dto.FeedbackStatusUpdateRequest;
import org.grit.daynomy.feedback.exception.FeedbackErrorCode;
import org.grit.daynomy.feedback.repository.FeedbackRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class FeedbackService {

  private final FeedbackRepository feedbackRepository;

  @Transactional
  public void create(FeedbackCreateRequest request) {
    feedbackRepository.save(Feedback.create(request.content().strip(), request.pagePath().strip()));
  }

  public AdminFeedbackPageResponse getPage(int page, int size) {
    PageRequest pageable =
        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return AdminFeedbackPageResponse.from(feedbackRepository.findAll(pageable));
  }

  public AdminFeedbackResponse getDetail(Long id) {
    return AdminFeedbackResponse.from(getFeedback(id));
  }

  @Transactional
  public AdminFeedbackResponse updateStatus(Long id, FeedbackStatusUpdateRequest request) {
    Feedback feedback = getFeedback(id);
    feedback.changeStatus(request.status());
    return AdminFeedbackResponse.from(feedback);
  }

  @Transactional
  public void delete(Long id) {
    feedbackRepository.delete(getFeedback(id));
  }

  private Feedback getFeedback(Long id) {
    return feedbackRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));
  }
}
