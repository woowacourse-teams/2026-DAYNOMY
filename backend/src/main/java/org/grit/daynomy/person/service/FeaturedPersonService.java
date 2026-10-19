package org.grit.daynomy.person.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.dto.SocialPostResponse;
import org.grit.daynomy.content.dto.SocialPostSearchResponse;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.external.s3.S3ImageStorage;
import org.grit.daynomy.external.wikimedia.WikimediaImageClient;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.domain.FeaturedPersonPostStatus;
import org.grit.daynomy.person.dto.FeaturedPeopleResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostRequest;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.dto.FeaturedPersonRequest;
import org.grit.daynomy.person.dto.FeaturedPersonResponse;
import org.grit.daynomy.person.exception.FeaturedPersonErrorCode;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
@Service
public class FeaturedPersonService {

  private static final Pattern YOUTUBE_VIDEO_ID_PARAMETER =
      Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)");

  private final FeaturedPersonRepository personRepository;
  private final FeaturedPersonPostRepository postRepository;
  private final OpenAiSocialPostSearchClient socialPostSearchClient;
  private final WikimediaImageClient wikimediaImageClient;
  private final S3ImageStorage s3ImageStorage;

  public FeaturedPeopleResponse getPeople() {
    Map<Long, List<FeaturedPersonPost>> postsByPersonId =
        postRepository
            .findAllByStatusOrderByCreatedAtDescIdDesc(FeaturedPersonPostStatus.APPROVED)
            .stream()
            .sorted(
                Comparator.comparing(
                    FeaturedPersonPost::getPublishedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.groupingBy(post -> post.getPerson().getId()));
    return new FeaturedPeopleResponse(
        personRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
            .map(
                person ->
                    FeaturedPersonResponse.from(
                        person, postsByPersonId.getOrDefault(person.getId(), List.of())))
            .toList());
  }

  public SocialPostSearchResponse searchPosts(Long personId) {
    FeaturedPerson person = getPerson(personId);
    String searchTarget =
        Stream.of(person.getName(), person.getXProfileUrl(), person.getThreadsProfileUrl())
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(" "));
    return new SocialPostSearchResponse(
        socialPostSearchClient.search(searchTarget).stream()
            .map(SocialPostResponse::from)
            .toList());
  }

  @Transactional
  public FeaturedPersonResponse createPerson(FeaturedPersonRequest request) {
    String name = request.name().strip();
    if (personRepository.existsByName(name)) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_ALREADY_EXISTS);
    }
    FeaturedPerson person =
        FeaturedPerson.create(
            name,
            normalizeProfileUrl(request.xProfileUrl(), this::isXProfile),
            normalizeProfileUrl(request.threadsProfileUrl(), this::isThreadsProfile),
            normalizeProfileUrl(request.youtubeProfileUrl(), this::isYouTubeProfile),
            uploadProfileImage(request));
    FeaturedPerson savedPerson = personRepository.saveAndFlush(person);
    log.atInfo()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_CREATED.code())
        .addKeyValue("personId", savedPerson.getId())
        .log(LogEvent.FEATURED_PERSON_CREATED.message());
    return FeaturedPersonResponse.from(savedPerson, List.of());
  }

  @Transactional
  public FeaturedPersonResponse updatePerson(Long personId, FeaturedPersonRequest request) {
    FeaturedPerson person = getPerson(personId);
    String name = request.name().strip();
    if (personRepository.existsByNameAndIdNot(name, personId)) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_ALREADY_EXISTS);
    }
    String oldProfileImageUrl = person.getProfileImageUrl();
    String profileImageUrl =
        request.profileImageSelection() == null ? oldProfileImageUrl : uploadProfileImage(request);
    person.update(
        name,
        normalizeProfileUrl(request.xProfileUrl(), this::isXProfile),
        normalizeProfileUrl(request.threadsProfileUrl(), this::isThreadsProfile),
        normalizeProfileUrl(request.youtubeProfileUrl(), this::isYouTubeProfile),
        profileImageUrl);
    if (request.profileImageSelection() != null) {
      s3ImageStorage.deleteIfManaged(oldProfileImageUrl);
    }
    log.atInfo()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_UPDATED.code())
        .addKeyValue("personId", personId)
        .addKeyValue("profileImageChanged", request.profileImageSelection() != null)
        .log(LogEvent.FEATURED_PERSON_UPDATED.message());
    return FeaturedPersonResponse.from(person, postsFor(personId));
  }

  @Transactional
  public void deletePerson(Long personId) {
    FeaturedPerson person = getPerson(personId);
    personRepository.delete(person);
    s3ImageStorage.deleteIfManaged(person.getProfileImageUrl());
    log.atInfo()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_DELETED.code())
        .addKeyValue("personId", personId)
        .log(LogEvent.FEATURED_PERSON_DELETED.message());
  }

  @Transactional
  public FeaturedPersonPostResponse createPost(Long personId, FeaturedPersonPostRequest request) {
    FeaturedPerson person = getPerson(personId);
    ContentSourceType sourceType = request.sourceType();
    String url = normalizePostUrl(request.url(), sourceType);
    if (postRepository.existsByPersonIdAndUrl(personId, url)) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_POST_ALREADY_EXISTS);
    }
    String title = stripToNull(request.title());
    if (title == null) {
      title = person.getName() + " " + sourceLabel(sourceType) + " 게시물";
    }
    FeaturedPersonPost post =
        FeaturedPersonPost.create(person, sourceType, title, url, request.publishedAt());
    FeaturedPersonPost savedPost = postRepository.saveAndFlush(post);
    log.atInfo()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_CREATED.code())
        .addKeyValue("personId", personId)
        .addKeyValue("postId", savedPost.getId())
        .addKeyValue("sourceType", sourceType)
        .log(LogEvent.FEATURED_PERSON_POST_CREATED.message());
    return FeaturedPersonPostResponse.from(savedPost);
  }

  @Transactional
  public void deletePost(Long personId, Long postId) {
    getPerson(personId);
    FeaturedPersonPost post =
        postRepository
            .findByIdAndPersonId(postId, personId)
            .orElseThrow(
                () ->
                    new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_POST_NOT_FOUND));
    postRepository.delete(post);
    log.atInfo()
        .addKeyValue("event", LogEvent.FEATURED_PERSON_POST_DELETED.code())
        .addKeyValue("personId", personId)
        .addKeyValue("postId", postId)
        .addKeyValue("sourceType", post.getSourceType())
        .log(LogEvent.FEATURED_PERSON_POST_DELETED.message());
  }

  private FeaturedPerson getPerson(Long personId) {
    return personRepository
        .findById(personId)
        .orElseThrow(
            () -> new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_NOT_FOUND));
  }

  private List<FeaturedPersonPost> postsFor(Long personId) {
    return postRepository
        .findByPersonIdAndStatusOrderByCreatedAtDescIdDesc(
            personId, FeaturedPersonPostStatus.APPROVED)
        .stream()
        .sorted(
            Comparator.comparing(
                FeaturedPersonPost::getPublishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private String normalizeProfileUrl(String value, Predicate<URI> validator) {
    String stripped = stripToNull(value);
    if (stripped == null) return null;
    URI uri = parseHttpsUri(stripped);
    if (!validator.test(uri)) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);
    }
    return normalize(uri);
  }

  String normalizePostUrl(String value, ContentSourceType sourceType) {
    if (sourceType != ContentSourceType.X
        && sourceType != ContentSourceType.THREADS
        && sourceType != ContentSourceType.YOUTUBE) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);
    }
    URI uri = parseHttpsUri(value.strip());
    boolean valid =
        sourceType == ContentSourceType.X
            ? isXPost(uri)
            : sourceType == ContentSourceType.THREADS ? isThreadsPost(uri) : isYouTubeVideo(uri);
    if (!valid) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);
    }
    if (sourceType == ContentSourceType.YOUTUBE && uri.getPath().equals("/watch")) {
      return "https://www.youtube.com/watch?v=" + extractYouTubeVideoId(uri);
    }
    return normalize(uri);
  }

  private URI parseHttpsUri(String value) {
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        throw new IllegalArgumentException();
      }
      return uri;
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);
    }
  }

  private String normalize(URI uri) {
    try {
      String path =
          uri.getPath().endsWith("/")
              ? uri.getPath().substring(0, uri.getPath().length() - 1)
              : uri.getPath();
      return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), -1, path, null, null)
          .toString();
    } catch (URISyntaxException exception) {
      throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);
    }
  }

  private boolean isXProfile(URI uri) {
    return isXHost(uri) && uri.getPath().matches("/[^/]+/?");
  }

  private boolean isThreadsProfile(URI uri) {
    return isThreadsHost(uri) && uri.getPath().matches("/@[^/]+/?");
  }

  private boolean isXPost(URI uri) {
    return isXHost(uri) && uri.getPath().matches("/[^/]+/status/\\d+/?");
  }

  private boolean isThreadsPost(URI uri) {
    return isThreadsHost(uri)
        && (uri.getPath().matches("(?i)/@[^/]+/post/[^/]+/?")
            || uri.getPath().matches("(?i)/t/[^/]+/?"));
  }

  private boolean isXHost(URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    return host.equals("x.com")
        || host.equals("www.x.com")
        || host.equals("twitter.com")
        || host.equals("www.twitter.com");
  }

  private boolean isThreadsHost(URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    return host.equals("threads.net")
        || host.equals("www.threads.net")
        || host.equals("threads.com")
        || host.equals("www.threads.com");
  }

  private boolean isYouTubeVideo(URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    if (host.equals("youtu.be")) return uri.getPath().matches("/[A-Za-z0-9_-]{11}/?");
    if (!host.equals("youtube.com")) return false;
    return uri.getPath().matches("/(embed|shorts|live)/[A-Za-z0-9_-]{11}/?")
        || (uri.getPath().equals("/watch") && extractYouTubeVideoId(uri) != null);
  }

  private String extractYouTubeVideoId(URI uri) {
    String query = uri.getRawQuery();
    if (query == null) return null;
    Matcher matcher = YOUTUBE_VIDEO_ID_PARAMETER.matcher(query);
    return matcher.find() ? matcher.group(1) : null;
  }

  private boolean isYouTubeProfile(URI uri) {
    String host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    return host.equals("youtube.com")
        && (uri.getPath().matches("/@[^/]+/?")
            || uri.getPath().matches("/(channel|c|user)/[^/]+/?"));
  }

  private String sourceLabel(ContentSourceType sourceType) {
    return switch (sourceType) {
      case X -> "X";
      case THREADS -> "Threads";
      case YOUTUBE -> "YouTube";
      default ->
          throw new BusinessException(FeaturedPersonErrorCode.FEATURED_PERSON_INVALID_SOCIAL_URL);
    };
  }

  private String uploadProfileImage(FeaturedPersonRequest request) {
    if (request.profileImageSelection() == null) return null;
    WikimediaImageClient.ImportedImage importedImage =
        wikimediaImageClient.download(request.profileImageSelection().title());
    return s3ImageStorage
        .upload(importedImage.content(), importedImage.extension(), importedImage.contentType())
        .publicUrl();
  }

  private String stripToNull(String value) {
    if (value == null) return null;
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
