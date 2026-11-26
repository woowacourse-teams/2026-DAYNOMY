package org.grit.daynomy.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.service.GuestPortfolioAnalysisTrial;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GuestPortfolioAnalysisTrialRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuestPortfolioAnalysisTrialPersistenceTest extends SharedDatabaseTest {

  @Autowired private GuestPortfolioAnalysisTrialRepository repository;
  private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
  private final PortfolioAnalysisRequest input =
      new PortfolioAnalysisRequest(
          List.of(new PortfolioAssetRequest("삼성전자", new BigDecimal("100"))));

  @Test
  void anotherApplicationCanReadBackgroundProgressAndTheCompletedResult() {
    String session = UUID.randomUUID().toString();
    var oldApplication = trial(session);
    var prepared = oldApplication.reserve(input);
    UUID id = oldApplication.get().taskId();

    var newApplication = trial(session);
    assertThat(newApplication.get(id).status()).isEqualTo("PROCESSING");
    assertThat(newApplication.reserve(input).token()).isNull();
    var core = PortfolioAnalysisResponse.empty();
    assertThat(prepared.task().publishCore(prepared.token(), core)).isTrue();
    assertThat(newApplication.get(id).analysis()).isEqualTo(core);
    var completed = core.withOverallImpact("분석을 완료했어요.");
    prepared.task().complete(prepared.token(), completed);

    var afterRetirement = trial(session).get(id);
    assertThat(afterRetirement.status()).isEqualTo("READY");
    assertThat(afterRetirement.analysis()).isEqualTo(completed);
    assertThat(trial(session).reserve(input).token()).isNull();
  }

  @Test
  void concurrentApplicationsReserveOnlyOneAnalysisAttempt() throws Exception {
    String session = UUID.randomUUID().toString();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return trial(session).reserve(input);
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return trial(session).reserve(input);
              });
      start.countDown();
      var reservations = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertThat(reservations.stream().filter(item -> item.token() != null).count()).isEqualTo(1);
      assertThat(reservations.get(0).task().response().taskId())
          .isEqualTo(reservations.get(1).task().response().taskId());
    }
  }

  @Test
  void anotherSessionCannotReadOrRetryAnExistingTask() {
    var owner = trial(UUID.randomUUID().toString());
    owner.reserve(input);
    UUID id = owner.get().taskId();
    var other = trial(UUID.randomUUID().toString());
    assertThatThrownBy(() -> other.get(id)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> other.retry(id)).isInstanceOf(BusinessException.class);
  }

  private GuestPortfolioAnalysisTrial trial(String session) {
    var request = new MockHttpServletRequest();
    request.setSession(new MockHttpSession(null, session));
    return new GuestPortfolioAnalysisTrial(request, repository, mapper);
  }
}
