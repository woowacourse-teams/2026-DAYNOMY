package org.grit.daynomy.feedback.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
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
@Slf4j
@RequiredArgsConstructor
@Service
public class FeedbackService {

  private final FeedbackRepository feedbackRepository;

  @Transactional
  public void create(FeedbackCreateRequest request) {
    Feedback feedback = Feedback.create(request.content().strip(), request.pagePath().strip());
    feedbackRepository.save(feedback);
    log.atInfo()
        .addKeyValue("event", LogEvent.FEEDBACK_CREATED.code())
        .addKeyValue("feedbackId", feedback.getId())
        .log(LogEvent.FEEDBACK_CREATED.message());
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
    log.atInfo()
        .addKeyValue("event", LogEvent.FEEDBACK_STATUS_UPDATED.code())
        .addKeyValue("feedbackId", id)
        .addKeyValue("status", request.status())
        .log(LogEvent.FEEDBACK_STATUS_UPDATED.message());
    return AdminFeedbackResponse.from(feedback);
  }

  @Transactional
  public void delete(Long id) {
    feedbackRepository.delete(getFeedback(id));
    log.atInfo()
        .addKeyValue("event", LogEvent.FEEDBACK_DELETED.code())
        .addKeyValue("feedbackId", id)
        .log(LogEvent.FEEDBACK_DELETED.message());
  }

  private Feedback getFeedback(Long id) {
    return feedbackRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(FeedbackErrorCode.FEEDBACK_NOT_FOUND));
  }
}
