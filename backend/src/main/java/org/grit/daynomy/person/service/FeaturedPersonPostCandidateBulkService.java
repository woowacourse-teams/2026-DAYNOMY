package org.grit.daynomy.person.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse.Failure;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateBulkResponse.Success;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class FeaturedPersonPostCandidateBulkService {

  private final FeaturedPersonPostCollectionService collectionService;

  public FeaturedPersonPostCandidateBulkResponse approve(Long personId, List<Long> candidateIds) {
    List<Success> succeeded = new ArrayList<>();
    List<Failure> failed = new ArrayList<>();
    for (Long candidateId : candidateIds) {
      try {
        succeeded.add(new Success(candidateId, collectionService.approve(personId, candidateId)));
      } catch (BusinessException exception) {
        failed.add(
            new Failure(
                candidateId, exception.errorCode().code(), exception.errorCode().message()));
      } catch (RuntimeException exception) {
        logUnexpectedFailure(personId, candidateId, "approve", exception);
        failed.add(new Failure(candidateId, "INTERNAL_ERROR", "게시물 후보를 승인하지 못했습니다."));
      }
    }
    return new FeaturedPersonPostCandidateBulkResponse(List.copyOf(succeeded), List.copyOf(failed));
  }

  public FeaturedPersonPostCandidateBulkResponse approve(List<Long> candidateIds) {
    List<Success> succeeded = new ArrayList<>();
    List<Failure> failed = new ArrayList<>();
    for (Long candidateId : candidateIds) {
      try {
        succeeded.add(new Success(candidateId, collectionService.approve(candidateId)));
      } catch (BusinessException exception) {
        failed.add(
            new Failure(
                candidateId, exception.errorCode().code(), exception.errorCode().message()));
      } catch (RuntimeException exception) {
        logUnexpectedFailure(null, candidateId, "approve", exception);
        failed.add(new Failure(candidateId, "INTERNAL_ERROR", "게시물 후보를 승인하지 못했습니다."));
      }
    }
    return new FeaturedPersonPostCandidateBulkResponse(List.copyOf(succeeded), List.copyOf(failed));
  }

  public FeaturedPersonPostCandidateBulkResponse reject(Long personId, List<Long> candidateIds) {
    List<Success> succeeded = new ArrayList<>();
    List<Failure> failed = new ArrayList<>();
    for (Long candidateId : candidateIds) {
      try {
        collectionService.reject(personId, candidateId);
        succeeded.add(new Success(candidateId, null));
      } catch (BusinessException exception) {
        failed.add(
            new Failure(
                candidateId, exception.errorCode().code(), exception.errorCode().message()));
      } catch (RuntimeException exception) {
        logUnexpectedFailure(personId, candidateId, "reject", exception);
        failed.add(new Failure(candidateId, "INTERNAL_ERROR", "게시물 후보를 거절하지 못했습니다."));
      }
    }
    return new FeaturedPersonPostCandidateBulkResponse(List.copyOf(succeeded), List.copyOf(failed));
  }

  public FeaturedPersonPostCandidateBulkResponse reject(List<Long> candidateIds) {
    List<Success> succeeded = new ArrayList<>();
    List<Failure> failed = new ArrayList<>();
    for (Long candidateId : candidateIds) {
      try {
        collectionService.reject(candidateId);
        succeeded.add(new Success(candidateId, null));
      } catch (BusinessException exception) {
        failed.add(
            new Failure(
                candidateId, exception.errorCode().code(), exception.errorCode().message()));
      } catch (RuntimeException exception) {
        logUnexpectedFailure(null, candidateId, "reject", exception);
        failed.add(new Failure(candidateId, "INTERNAL_ERROR", "게시물 후보를 거절하지 못했습니다."));
      }
    }
    return new FeaturedPersonPostCandidateBulkResponse(List.copyOf(succeeded), List.copyOf(failed));
  }

  private void logUnexpectedFailure(
      Long personId, Long candidateId, String action, RuntimeException exception) {
    log.atError()
        .addKeyValue("personId", personId)
        .addKeyValue("candidateId", candidateId)
        .addKeyValue("action", action)
        .addKeyValue("exceptionType", exception.getClass().getSimpleName())
        .log("Featured person post candidate bulk processing failed unexpectedly");
  }
}
