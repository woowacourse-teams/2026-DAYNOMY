package org.grit.daynomy.person.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.youtube.YouTubeClient;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidateResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostCandidatesResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class FeaturedPersonPostCollectionService {

  private static final int MAX_COLLECTION_DAYS = 31;
  private static final ZoneId COLLECTION_ZONE = ZoneId.of("Asia/Seoul");

  private final FeaturedPersonRepository personRepository;
  private final FeaturedPersonPostRepository postRepository;
  private final OpenAiSocialPostSearchClient socialPostSearchClient;
  private final YouTubeClient youtubeClient;
  private final FeaturedPersonService featuredPersonService;

  @Transactional(readOnly = true)
  public FeaturedPersonPostCandidatesResponse getPendingCandidates(Long personId) {
    getPerson(personId);
    return pendingCandidates(personId);
  }

  @Transactional
  public FeaturedPersonPostCandidatesResponse collect(Long personId, LocalDate from, LocalDate to) {
    long startedAt = System.nanoTime();
    validatePeriod(from, to);
    FeaturedPerson person = getPerson(personId);
    List<CollectedPost> collected = new ArrayList<>();
    BusinessException firstFailure = null;
    int sourceFailureCount = 0;
    log.atDebug()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_STARTED.code())
        .addKeyValue("personId", personId)
        .addKeyValue("from", from)
        .addKeyValue("to", to)
        .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_STARTED.message());

    if (person.getXProfileUrl() != null || person.getThreadsProfileUrl() != null) {
      try {
        socialPostSearchClient
            .searchProfiles(person.getXProfileUrl(), person.getThreadsProfileUrl(), from, to)
            .forEach(
                post ->
                    collected.add(
                        new CollectedPost(
                            ContentSourceType.valueOf(post.platform().name()),
                            post.title(),
                            post.url(),
                            parseDate(post.publishedDate()))));
      } catch (BusinessException exception) {
        firstFailure = exception;
        sourceFailureCount++;
        logCollectionFailure(personId, "social", exception);
      }
    }

    if (person.getYoutubeProfileUrl() != null) {
      try {
        youtubeClient
            .searchProfile(
                person.getYoutubeProfileUrl(),
                from.atStartOfDay(COLLECTION_ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(COLLECTION_ZONE).toInstant())
            .forEach(
                video ->
                    collected.add(
                        new CollectedPost(
                            ContentSourceType.YOUTUBE,
                            video.title(),
                            video.url(),
                            parseInstant(video.publishedAt()))));
      } catch (BusinessException exception) {
        if (firstFailure == null) {
          firstFailure = exception;
        }
        sourceFailureCount++;
        logCollectionFailure(personId, "youtube", exception);
      }
    }

    if (collected.isEmpty() && firstFailure != null) {
      throw firstFailure;
    }

    int createdCount = 0;
    int skippedCount = 0;
    for (CollectedPost post : collected) {
      try {
        String normalizedUrl =
            featuredPersonService.normalizePostUrl(post.url(), post.sourceType());
        if (postRepository.existsByPersonIdAndUrl(personId, normalizedUrl)) {
          skippedCount++;
          continue;
        }
        postRepository.save(
            FeaturedPersonPost.createPending(
                person, post.sourceType(), post.title(), normalizedUrl, post.publishedAt()));
        createdCount++;
      } catch (BusinessException exception) {
        skippedCount++;
        log.atWarn()
            .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_SKIPPED.code())
            .addKeyValue("personId", personId)
            .addKeyValue("sourceType", post.sourceType())
            .addKeyValue("errorCode", exception.errorCode().code())
            .addKeyValue("reasonCode", "INVALID_URL")
            .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_SKIPPED.message());
      }
    }
    postRepository.flush();
    FeaturedPersonPostCandidatesResponse response = pendingCandidates(personId);
    log.atInfo()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_COMPLETED.code())
        .addKeyValue("personId", personId)
        .addKeyValue("collectedCount", collected.size())
        .addKeyValue("createdCount", createdCount)
        .addKeyValue("skippedCount", skippedCount)
        .addKeyValue("sourceFailureCount", sourceFailureCount)
        .addKeyValue("pendingCount", response.candidates().size())
        .addKeyValue("durationMs", elapsedMillis(startedAt))
        .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_COMPLETED.message());
    return response;
  }

  @Transactional
  public FeaturedPersonPostResponse approve(Long personId, Long candidateId) {
    FeaturedPersonPost candidate = getPendingCandidate(personId, candidateId);
    candidate.approve(Instant.now());
    logCandidateStatusChanged(
        LogEvent.FEATURED_PERSON_POST_CANDIDATE_APPROVED, personId, candidate);
    return FeaturedPersonPostResponse.from(candidate);
  }

  @Transactional
  public FeaturedPersonPostResponse approve(Long candidateId) {
    FeaturedPersonPost candidate = getPendingCandidate(candidateId);
    candidate.approve(Instant.now());
    logCandidateStatusChanged(
        LogEvent.FEATURED_PERSON_POST_CANDIDATE_APPROVED,
        candidate.getPerson() == null ? null : candidate.getPerson().getId(),
        candidate);
    return FeaturedPersonPostResponse.from(candidate);
  }

  @Transactional
  public void reject(Long personId, Long candidateId) {
    FeaturedPersonPost candidate = getPendingCandidate(personId, candidateId);
    candidate.reject(Instant.now());
    logCandidateStatusChanged(
        LogEvent.FEATURED_PERSON_POST_CANDIDATE_REJECTED, personId, candidate);
  }

  @Transactional
  public void reject(Long candidateId) {
    FeaturedPersonPost candidate = getPendingCandidate(candidateId);
    candidate.reject(Instant.now());
    logCandidateStatusChanged(
        LogEvent.FEATURED_PERSON_POST_CANDIDATE_REJECTED,
        candidate.getPerson() == null ? null : candidate.getPerson().getId(),
        candidate);
  }

  private FeaturedPersonPostCandidatesResponse pendingCandidates(Long personId) {
    return new FeaturedPersonPostCandidatesResponse(
        postRepository
            .findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(
                personId, FeaturedPersonPostStatus.PENDING)
            .stream()
            .map(FeaturedPersonPostCandidateResponse::from)
            .toList());
  }

  private FeaturedPerson getPerson(Long personId) {
    return personRepository
        .findById(personId)
        .orElseThrow(
            () -> new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_NOT_FOUND));
  }

  private void validatePeriod(LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) >= MAX_COLLECTION_DAYS) {
      throw new BusinessException(
          FeaturedPersonErrorCode.FEATURED_PERSON_POST_COLLECTION_PERIOD_INVALID);
    }
  }

  private FeaturedPersonPost getPendingCandidate(Long personId, Long candidateId) {
    FeaturedPersonPost candidate =
        postRepository
            .findByIdAndPersonId(candidateId, personId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_FOUND));
    if (candidate.getStatus() != FeaturedPersonPostStatus.PENDING) {
      throw new BusinessException(
          FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING);
    }
    return candidate;
  }

  private FeaturedPersonPost getPendingCandidate(Long candidateId) {
    FeaturedPersonPost candidate =
        postRepository
            .findById(candidateId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_FOUND));
    if (candidate.getStatus() != FeaturedPersonPostStatus.PENDING) {
      throw new BusinessException(
          FeaturedPersonErrorCode.FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING);
    }
    return candidate;
  }

  private Instant parseDate(String value) {
    try {
      return value == null || value.isBlank()
          ? null
          : LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private Instant parseInstant(String value) {
    try {
      return value == null || value.isBlank() ? null : Instant.parse(value);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private void logCollectionFailure(Long personId, String source, BusinessException exception) {
    log.atWarn()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_COLLECTION_FAILED.code())
        .addKeyValue("personId", personId)
        .addKeyValue("source", source)
        .addKeyValue("errorCode", exception.errorCode().code())
        .log(LogEvent.FEATURED_PERSON_POST_COLLECTION_FAILED.message());
  }

  private void logCandidateStatusChanged(
      LogEvent logEvent, Long personId, FeaturedPersonPost candidate) {
    var event =
        log.atInfo()
            .addKeyValue("event", logEvent.code())
            .addKeyValue("candidateId", candidate.getId())
            .addKeyValue("sourceType", candidate.getSourceType());
    if (personId != null) {
      event.addKeyValue("personId", personId);
    }
    event.log(logEvent.message());
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private record CollectedPost(
      ContentSourceType sourceType, String title, String url, Instant publishedAt) {}
}
