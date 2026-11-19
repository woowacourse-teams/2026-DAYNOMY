package org.grit.daynomy.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.content.domain.CollectedExternalContent;
import org.grit.daynomy.content.domain.CollectedExternalContentReview;
import org.grit.daynomy.content.domain.ContentCollectionReviewStatus;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CollectedExternalContentRepositoryTest extends SharedDatabaseTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private CollectedExternalContentRepository repository;

  @Test
  void pendingCandidatesExcludeContentWithoutSuggestedAsset() {
    Asset asset = entityManager.persist(new Asset("삼성전자", AssetCategory.STOCK, "005930"));
    ContentCollectionSource source =
        entityManager.persist(
            new ContentCollectionSource(
                "경제 채널", ContentSourceType.YOUTUBE, "https://youtube.com/@economy", null, true));
    CollectedExternalContent matched =
        entityManager.persist(
            new CollectedExternalContent(
                source,
                "삼성전자 실적",
                "",
                "https://youtube.com/watch?v=matched",
                null,
                null));
    CollectedExternalContent unmatched =
        entityManager.persist(
            new CollectedExternalContent(
                source,
                "시장 전망",
                "",
                "https://youtube.com/watch?v=unmatched",
                null,
                null));
    entityManager.persist(new CollectedExternalContentReview(matched, asset));
    entityManager.persist(new CollectedExternalContentReview(unmatched, null));
    entityManager.flush();
    entityManager.clear();

    var result =
        repository.findAllWithReviewStatus(
            ContentCollectionReviewStatus.PENDING, PageRequest.of(0, 20));

    assertThat(result.getContent())
        .extracting(CollectedExternalContent::getTitle)
        .containsExactly("삼성전자 실적");
  }
}
