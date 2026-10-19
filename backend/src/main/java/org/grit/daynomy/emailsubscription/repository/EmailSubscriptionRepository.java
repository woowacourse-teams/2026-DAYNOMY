package org.grit.daynomy.emailsubscription.repository;

import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {}
