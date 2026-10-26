package org.grit.daynomy.person.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeaturedPersonPostCollectionSchedulerTest {

  @Mock private FeaturedPersonRepository personRepository;
  @Mock private FeaturedPersonPostCollectionService collectionService;

  @Test
  void onePersonFailureDoesNotStopTheRemainingPeople() {
    FeaturedPerson first = org.mockito.Mockito.mock(FeaturedPerson.class);
    FeaturedPerson second = org.mockito.Mockito.mock(FeaturedPerson.class);
    given(first.getId()).willReturn(1L);
    given(second.getId()).willReturn(2L);
    given(personRepository.findAllByOrderByCreatedAtAscIdAsc()).willReturn(List.of(first, second));
    given(collectionService.collect(org.mockito.ArgumentMatchers.eq(1L), any(), any()))
        .willThrow(new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_NOT_FOUND));

    new FeaturedPersonPostCollectionScheduler(personRepository, collectionService, 3).collect();

    LocalDate to = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
    LocalDate from = to.minusDays(2);
    then(collectionService).should().collect(1L, from, to);
    then(collectionService).should().collect(2L, from, to);
  }
}
