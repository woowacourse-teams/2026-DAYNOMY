package org.grit.daynomy.person.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
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
    validatePeriod(from, to);
    FeaturedPerson person = getPerson(personId);
    List<CollectedPost> collected = new ArrayList<>();
    BusinessException firstFailure = null;

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
        logCollectionFailure(personId, "youtube", exception);
      }
    }

    if (collected.isEmpty() && firstFailure != null) {
      throw firstFailure;
    }

    for (CollectedPost post : collected) {
      try {
        String normalizedUrl =
            featuredPersonService.normalizePostUrl(post.url(), post.sourceType());
        if (postRepository.existsByPersonIdAndUrl(personId, normalizedUrl)) {
          continue;
        }
        postRepository.save(
            FeaturedPersonPost.createPending(
                person, post.sourceType(), post.title(), normalizedUrl, post.publishedAt()));
      } catch (BusinessException exception) {
        log.atWarn()
            .addKeyValue("personId", personId)
            .addKeyValue("sourceType", post.sourceType())
            .addKeyValue("errorCode", exception.errorCode().code())
            .log("Featured person post candidate skipped because its URL is invalid");
      }
    }
    postRepository.flush();
    return pendingCandidates(personId);
  }

  @Transactional
  public FeaturedPersonPostResponse approve(Long personId, Long candidateId) {
    FeaturedPersonPost candidate = getPendingCandidate(personId, candidateId);
    candidate.approve(Instant.now());
    return FeaturedPersonPostResponse.from(candidate);
  }

  @Transactional
  public FeaturedPersonPostResponse approve(Long candidateId) {
    FeaturedPersonPost candidate = getPendingCandidate(candidateId);
    candidate.approve(Instant.now());
    return FeaturedPersonPostResponse.from(candidate);
  }

  @Transactional
  public void reject(Long personId, Long candidateId) {
    getPendingCandidate(personId, candidateId).reject(Instant.now());
  }

  @Transactional
  public void reject(Long candidateId) {
    getPendingCandidate(candidateId).reject(Instant.now());
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
        .addKeyValue("personId", personId)
        .addKeyValue("source", source)
        .addKeyValue("errorCode", exception.errorCode().code())
        .log("Featured person post collection source failed");
  }

  private record CollectedPost(
      ContentSourceType sourceType, String title, String url, Instant publishedAt) {}
}
