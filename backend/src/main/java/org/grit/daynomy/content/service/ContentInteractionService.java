package org.grit.daynomy.content.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentType;
import org.grit.daynomy.content.dto.ContentInteractionRequest;
import org.grit.daynomy.content.dto.ContentPopularityItemResponse;
import org.grit.daynomy.content.dto.ContentPopularityResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.content.repository.ContentInteractionRepository;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ContentInteractionService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ContentInteractionRepository interactionRepository;
  private final NewsRepository newsRepository;
  private final FeaturedPersonPostRepository personPostRepository;
  private final AssetContentRepository assetContentRepository;
  private final ContentInteractionRateLimiter rateLimiter;
  private final Clock clock;

  @Transactional
  public void record(
      ContentInteractionRequest request, AuthenticatedMember member, UUID anonymousVisitorId) {
    if (member != null && member.role() == MemberRole.ADMIN) {
      return;
    }
    String actor = interactionActor(member, anonymousVisitorId);
    rateLimiter.acquire(actor);
    validatePublicContent(request.contentType(), request.contentId());
    Instant now = Instant.now(clock);
    LocalDate interactionDate = now.atZone(SEOUL).toLocalDate();
    interactionRepository.insertIfAbsent(
        request.eventId(),
        deduplicationKey(actor, request, interactionDate),
        request.contentType().name(),
        request.contentId(),
        request.interactionType().name(),
        interactionDate,
        now);
  }

  @Transactional(readOnly = true)
  public ContentPopularityResponse getPopularity(int periodDays, int page, int size) {
    LocalDate fromDate = LocalDate.now(clock.withZone(SEOUL)).minusDays(periodDays - 1L);
    return ContentPopularityResponse.from(
        interactionRepository
            .findPopularitySince(fromDate, PageRequest.of(page - 1, size))
            .map(
                item ->
                    new ContentPopularityItemResponse(
                        ContentType.valueOf(item.getContentType()),
                        item.getContentId(),
                        item.getScore(),
                        item.getLastInteractedAt())),
        periodDays);
  }

  private void validatePublicContent(ContentType contentType, Long contentId) {
    boolean available =
        switch (contentType) {
          case NEWS -> newsRepository.existsByIdAndStatus(contentId, NewsStatus.PUBLISHED);
          case PERSON_POST ->
              personPostRepository.existsByIdAndStatus(
                  contentId, FeaturedPersonPostStatus.APPROVED);
          case ASSET_CONTENT ->
              assetContentRepository
                  .findById(contentId)
                  .map(AssetContent::getNews)
                  .map(news -> news.getStatus() == NewsStatus.PUBLISHED)
                  .orElseGet(() -> assetContentRepository.existsById(contentId));
        };
    if (!available) {
      throw new BusinessException(ContentErrorCode.CONTENT_NOT_AVAILABLE);
    }
  }

  private String interactionActor(AuthenticatedMember member, UUID anonymousVisitorId) {
    if (member != null) {
      return "member:" + member.memberId();
    }
    return "anonymous:" + Objects.requireNonNull(anonymousVisitorId);
  }

  private String deduplicationKey(
      String actor, ContentInteractionRequest request, LocalDate interactionDate) {
    String value =
        actor
            + ":"
            + request.contentType()
            + ":"
            + request.contentId()
            + ":"
            + request.interactionType()
            + ":"
            + interactionDate;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
    }
  }
}
