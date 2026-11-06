package org.grit.daynomy.news.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.external.openai.EconomicNewsResearch;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.repository.NewsGenerationTopicRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsGenerationTopicService {

  private final NewsGenerationTopicRepository topicRepository;

  @Transactional
  public List<NewsGenerationTopic> reserveReady(NewsGenerationType type, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    LocalDate firstDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(2);
    List<NewsGenerationTopic> topics =
        topicRepository.findReadyForUpdate(type.name(), firstDate, limit);
    topics.forEach(NewsGenerationTopic::reserve);
    return List.copyOf(topics);
  }

  @Transactional
  public List<NewsGenerationTopic> register(
      NewsGenerationType type, List<EconomicNewsResearch> researchItems) {
    Map<String, EconomicNewsResearch> itemsByKey = new LinkedHashMap<>();
    for (EconomicNewsResearch item : researchItems) {
      itemsByKey.putIfAbsent(topicKey(type, item), item);
    }
    if (itemsByKey.isEmpty()) {
      return List.of();
    }

    Set<String> existingKeys =
        topicRepository.findAllByTopicKeyIn(itemsByKey.keySet()).stream()
            .map(NewsGenerationTopic::getTopicKey)
            .collect(Collectors.toSet());
    List<NewsGenerationTopic> newTopics = new ArrayList<>();
    itemsByKey.forEach(
        (key, item) -> {
          if (!existingKeys.contains(key)) {
            newTopics.add(
                NewsGenerationTopic.create(
                    key,
                    type,
                    item.title(),
                    item.referenceDate(),
                    item.category(),
                    item.facts(),
                    item.sources()));
          }
        });
    return List.copyOf(topicRepository.saveAll(newTopics));
  }

  @Transactional(readOnly = true)
  public List<NewsGenerationTopic> findRecent(
      NewsGenerationType type, LocalDate firstDate, int limit) {
    return topicRepository
        .findByGenerationTypeAndReferenceDateGreaterThanEqualOrderByReferenceDateDescIdDesc(
            type, firstDate, PageRequest.of(0, limit));
  }

  @Transactional
  public void release(List<Long> topicIds) {
    if (topicIds.isEmpty()) {
      return;
    }
    topicRepository.findAllByIdForUpdate(topicIds).stream()
        .filter(NewsGenerationTopic::isReserved)
        .forEach(NewsGenerationTopic::release);
  }

  private String topicKey(NewsGenerationType type, EconomicNewsResearch item) {
    String sourceIdentity =
        item.sources().stream()
            .map(NewsSourceInfo::url)
            .map(this::normalizeUrl)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.joining("|"));
    String value = "v1|" + type.name() + "|" + item.referenceDate() + "|" + sourceIdentity;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  private String normalizeUrl(String value) {
    try {
      URI uri = URI.create(value.strip()).normalize();
      String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
      String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
      int port = uri.getPort();
      if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
        port = -1;
      }
      String path = uri.getPath();
      if (path == null || path.isBlank()) {
        path = "/";
      } else if (path.length() > 1 && path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      return new URI(scheme, uri.getUserInfo(), host, port, path, uri.getQuery(), null).toString();
    } catch (IllegalArgumentException | URISyntaxException exception) {
      throw new IllegalArgumentException("Invalid news source URL.", exception);
    }
  }
}
