package org.grit.daynomy.investmentcalendar.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentEventRepository extends JpaRepository<InvestmentEvent, Long> {

  Optional<InvestmentEvent> findBySourceKey(String sourceKey);

  List<InvestmentEvent>
      findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
          Instant from, Instant to);

  List<InvestmentEvent>
      findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
          InvestmentEventType type, Instant announcedAt);
}
