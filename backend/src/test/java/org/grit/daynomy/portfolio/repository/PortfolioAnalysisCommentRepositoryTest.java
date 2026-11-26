package org.grit.daynomy.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisComment;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPublicSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioAnalysisCommentRepositoryTest extends SharedDatabaseTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private PortfolioAnalysisCommentRepository comments;

  @Test
  void loadsRootsAndRepliesForMultipleParentsInStableOrder() {
    Member owner = persistMember("공유 작성자");
    Member reviewer = persistMember("공유 검토자");
    PortfolioAnalysisShare firstShare = persistShare(owner, "a");
    PortfolioAnalysisShare secondShare = persistShare(owner, "b");
    PortfolioAnalysisComment firstRoot =
        entityManager.persist(
            PortfolioAnalysisComment.root(firstShare, reviewer, "첫 번째 공유의 의견입니다."));
    PortfolioAnalysisComment secondRoot =
        entityManager.persist(
            PortfolioAnalysisComment.root(secondShare, reviewer, "두 번째 공유의 의견입니다."));
    PortfolioAnalysisComment firstReply =
        entityManager.persist(PortfolioAnalysisComment.reply(firstRoot, owner, "먼저 작성한 답글입니다."));
    PortfolioAnalysisComment secondReply =
        entityManager.persist(
            PortfolioAnalysisComment.reply(firstRoot, reviewer, "나중에 작성한 답글입니다."));
    entityManager.flush();
    entityManager.clear();

    var roots =
        comments.findAllByShareIdInAndParentIsNullOrderByCreatedAtAscIdAsc(
            List.of(firstShare.getId(), secondShare.getId()));
    var replies =
        comments.findAllByParentIdInOrderByCreatedAtAscIdAsc(
            List.of(firstRoot.getId(), secondRoot.getId()));
    var persistenceUnit =
        entityManager.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();

    assertThat(roots)
        .extracting(PortfolioAnalysisComment::getId)
        .containsExactly(firstRoot.getId(), secondRoot.getId());
    assertThat(roots)
        .allSatisfy(
            root -> {
              assertThat(persistenceUnit.isLoaded(root, "author")).isTrue();
              assertThat(persistenceUnit.isLoaded(root, "share")).isFalse();
            });
    assertThat(replies)
        .extracting(PortfolioAnalysisComment::getId)
        .containsExactly(firstReply.getId(), secondReply.getId());
    assertThat(roots).allSatisfy(root -> assertThat(root.getAuthor().getNickname()).isNotBlank());
    assertThat(replies)
        .allSatisfy(reply -> assertThat(reply.getAuthor().getNickname()).isNotBlank());
  }

  private Member persistMember(String nickname) {
    String identifier = UUID.randomUUID().toString();
    return entityManager.persist(
        Member.createGoogleMember(
            identifier,
            identifier + "@example.invalid",
            nickname + identifier.substring(0, 4),
            null));
  }

  private PortfolioAnalysisShare persistShare(Member owner, String tokenSeed) {
    return entityManager.persist(
        PortfolioAnalysisShare.create(
            owner,
            null,
            "공유 포트폴리오를 검토해 주세요.",
            tokenSeed.repeat(64),
            "encrypted-" + tokenSeed,
            snapshot(),
            Instant.parse("2026-11-25T00:00:00Z")));
  }

  private PortfolioAnalysisPublicSnapshot snapshot() {
    return new PortfolioAnalysisPublicSnapshot(
        0,
        0,
        true,
        false,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        "",
        List.of(),
        List.of(),
        List.of());
  }
}
