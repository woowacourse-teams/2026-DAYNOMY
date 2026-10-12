package org.grit.daynomy.feedback.repository;

import org.grit.daynomy.feedback.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {}
