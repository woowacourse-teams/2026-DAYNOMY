package org.grit.daynomy.news.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NewsGenerationTopicRepository extends JpaRepository<NewsGenerationTopic, Long> {

  @Query(
      value =
          """
          SELECT *
          FROM news_generation_topics
          WHERE generation_type = :generationType
            AND status = 'READY'
            AND reference_date >= :firstDate
          ORDER BY reference_date, id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<NewsGenerationTopic> findReadyForUpdate(
      String generationType, LocalDate firstDate, int limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from NewsGenerationTopic t where t.id in :ids order by t.id")
  List<NewsGenerationTopic> findAllByIdForUpdate(Collection<Long> ids);

  List<NewsGenerationTopic>
      findByGenerationTypeAndReferenceDateGreaterThanEqualOrderByReferenceDateDescIdDesc(
          NewsGenerationType generationType, LocalDate referenceDate, Pageable pageable);

  List<NewsGenerationTopic> findAllByTopicKeyIn(Collection<String> topicKeys);
}
