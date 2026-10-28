package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortfolioAnalysisDocumentClientTest {
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void readsKiaOfficialArticleApiAndRejectsAnotherArticleId(boolean matchingId) throws Exception {
    try (var server = new MockWebServer();
        var client = kiaSourceClient()) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/html")
              .setBody(
                  "<html><head><title>Loading</title></head><body><div id='app'></div></body></html>"));
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody(
                  """
            {"status":200,"data":{"id":%d,"headline":"기아 실적 발표","publishedAt":"2026-10-20 07:16",
             "mediaPostDataList":[{"type":"text","description":"<p>매출은 증가했어요.</p>"},
              {"type":"text","description":"<p>영업이익은 감소했어요.</p>"}]}}
            """
                      .formatted(matchingId ? 123 : 999)));
      var result =
          client.fetch(
              "http://worldwide.kia.com:" + server.getPort() + "/en/newsroom/view/?id=123",
              Duration.ofSeconds(3));
      if (matchingId) {
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().title()).isEqualTo("기아 실적 발표");
        assertThat(result.orElseThrow().body()).contains("매출은 증가했어요.", "영업이익은 감소했어요.");
        assertThat(result.orElseThrow().publishedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
      } else {
        assertThat(result).isEmpty();
      }
      assertThat(server.takeRequest().getPath()).isEqualTo("/en/newsroom/view/?id=123");
      assertThat(server.takeRequest().getPath()).isEqualTo("/api/media-lounge/id/123");
      assertThat(server.getRequestCount()).isEqualTo(2);
    }
  }

  @Test
  void doesNotApplyOfficialApiFallbackToAnotherHost() throws Exception {
    try (var server = new MockWebServer();
        var client = kiaSourceClient()) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/html")
              .setBody("<div id='app'></div>"));
      var result =
          client.fetch(
              "http://worldwide.kia.com.other.example:"
                  + server.getPort()
                  + "/en/newsroom/view/?id=123",
              Duration.ofSeconds(3));
      assertThat(result).isEmpty();
      assertThat(server.getRequestCount()).isEqualTo(1);
    }
  }

  private PortfolioAnalysisDocumentClient kiaSourceClient() {
    return new PortfolioAnalysisDocumentClient(
        PortfolioAnalysisDocumentClient.createHttpClient(
            new org.apache.hc.client5.http.DnsResolver() {
              @Override
              public InetAddress[] resolve(String host) {
                return new InetAddress[] {InetAddress.getLoopbackAddress()};
              }

              @Override
              public String resolveCanonicalHostname(String host) {
                return host;
              }
            }));
  }

  @Test
  void returnsAtBatchDeadlineEvenWhenSourceWorkIgnoresInterruption() throws Exception {
    var release =
        new java.util.concurrent.CompletableFuture<
            java.util.Optional<
                org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument>>();
    try (var client =
            new PortfolioAnalysisDocumentClient(HttpClients.createDefault()) {
              @Override
              public java.util.Optional<
                      org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument>
                  fetch(String url, Duration remaining) {
                return release.join();
              }
            };
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      try {
        var result =
            executor.submit(() -> client.fetchAll(java.util.List.of("https://example.com/slow")));
        assertThat(result.get(7, TimeUnit.SECONDS))
            .containsEntry("https://example.com/slow", java.util.Optional.empty());
      } finally {
        release.complete(java.util.Optional.empty());
      }
    }
  }

  @Test
  void readsFifteenSourcesFromOneHostConcurrentlyWithoutLosingEvidence() throws Exception {
    try (var server = new MockWebServer();
        var client =
            new PortfolioAnalysisDocumentClient(
                PortfolioAnalysisDocumentClient.createHttpClient(
                    org.apache.hc.client5.http.SystemDefaultDnsResolver.INSTANCE))) {
      server.start();
      var urls = new java.util.ArrayList<String>();
      for (int index = 0; index < 15; index++) {
        urls.add(server.url("/article/" + index).toString().replace("127.0.0.1", "localhost"));
        server.enqueue(
            new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<article>Confirmed facts.</article>")
                .setBodyDelay(500, TimeUnit.MILLISECONDS));
      }
      try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        var futures =
            urls.stream()
                .map(url -> executor.submit(() -> client.fetch(url, Duration.ofSeconds(3))))
                .toList();
        for (var future : futures)
          assertThat(future.get(4, TimeUnit.SECONDS))
              .hasValueSatisfying(
                  document -> assertThat(document.body()).isEqualTo("Confirmed facts."));
      }
      assertThat(server.getRequestCount()).isEqualTo(15);
    }
  }

  @Test
  void rejectsCompressedPdfWhoseExtractedTextExceedsTheLimit() throws Exception {
    byte[] bytes;
    try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
        var output = new java.io.ByteArrayOutputStream()) {
      var font =
          new org.apache.pdfbox.pdmodel.font.PDType1Font(
              org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
      for (int index = 0; index < 45; index++) {
        var page = new org.apache.pdfbox.pdmodel.PDPage();
        document.addPage(page);
        try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
          content.beginText();
          content.setFont(font, 12);
          content.newLineAtOffset(30, 700);
          content.showText("Revenue increased. ".repeat(700));
          content.endText();
        }
      }
      document.save(output);
      bytes = output.toByteArray();
    }
    assertThat(bytes.length).isLessThan(4 * 1024 * 1024);
    try (var server = new MockWebServer();
        var client = new PortfolioAnalysisDocumentClient(HttpClients.createDefault())) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/pdf")
              .setBody(new okio.Buffer().write(bytes)));
      assertThat(
              client.fetch(
                  server.url("/large.pdf").toString().replace("127.0.0.1", "localhost"),
                  Duration.ofSeconds(3)))
          .isEmpty();
    }
  }

  @Test
  void readsPublicationMetadataAndArticleInsteadOfModifiedDateAndNavigation() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <title>Q3 2022 results</title>
          <meta property="article:published_time" content="2022-10-26T08:00:00Z">
          <meta property="article:modified_time" content="2026-10-20T08:00:00Z">
          <nav>2026 news</nav><article>October 26, 2022. Memory demand weakened.</article>
          <footer>2026</footer>
          """);
      assertThat(document.publishedOn()).isEqualTo(LocalDate.of(2022, 10, 26));
      assertThat(document.body()).contains("Memory demand weakened.").doesNotContain("2026");
    }
  }

  @Test
  void jsonLdPublicationMustBelongToAnArticle() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <script type="application/ld+json">{"@graph":[
          {"@type":"WebSite","datePublished":"2000-01-01"},
          {"@type":"NewsArticle","datePublished":"2022-10-26","dateModified":"2026-10-20"}]}</script>
          <article>Facts</article>
          """);
      assertThat(document.publishedOn()).isEqualTo(LocalDate.of(2022, 10, 26));
      assertThat(client.parse("<article>2026-10-20</article>").publishedOn()).isNull();
      assertThat(
              client
                  .parse(
                      """
          <meta property="article:published_time" content="2022-10-26">
          <meta itemprop="datePublished" content="2026-10-20"><article>Facts</article>
          """)
                  .publishedOn())
          .isNull();
    }
  }

  @Test
  void fetchesHtmlButDoesNotFollowPrivateRedirectOrAcceptErrorsAndLargeBodies() throws Exception {
    try (var server = new MockWebServer();
        var client =
            new PortfolioAnalysisDocumentClient(
                HttpClients.custom().disableRedirectHandling().build())) {
      server.start();
      String url = server.url("/article").toString().replace("127.0.0.1", "localhost");
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/html")
              .setBody("<article>Facts</article>"));
      assertThat(client.fetch(url, Duration.ofSeconds(2))).isPresent();
      server.enqueue(
          new MockResponse()
              .setResponseCode(302)
              .setHeader("Location", "http://127.0.0.1/private"));
      assertThat(client.fetch(url, Duration.ofSeconds(2))).isEmpty();
      server.enqueue(new MockResponse().setResponseCode(403));
      assertThat(client.fetch(url, Duration.ofSeconds(2))).isEmpty();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/html")
              .setBody("a".repeat(512 * 1024 + 1)));
      assertThat(client.fetch(url, Duration.ofSeconds(2))).isEmpty();
      assertThat(server.getRequestCount()).isEqualTo(4);
    }
  }

  @Test
  void followsBoundedRedirectsAndReadsTheDestinationOriginal() throws Exception {
    try (var server = new MockWebServer();
        var client =
            new PortfolioAnalysisDocumentClient(
                HttpClients.custom().disableRedirectHandling().build())) {
      server.start();
      server.enqueue(new MockResponse().setResponseCode(301).setHeader("Location", "/canonical"));
      server.enqueue(new MockResponse().setResponseCode(308).setHeader("Location", "/article"));
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/html")
              .setBody("<main>Confirmed original.</main>"));
      var result =
          client.fetch(
              server.url("/redirect").toString().replace("127.0.0.1", "localhost"),
              Duration.ofSeconds(2));
      assertThat(result)
          .hasValueSatisfying(
              document -> assertThat(document.body()).isEqualTo("Confirmed original."));
      assertThat(server.getRequestCount()).isEqualTo(3);
      for (int n = 0; n < 3; n++)
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/loop"));
      assertThat(
              client.fetch(
                  server.url("/loop").toString().replace("127.0.0.1", "localhost"),
                  Duration.ofSeconds(2)))
          .isEmpty();
      assertThat(server.getRequestCount()).isEqualTo(6);
    }
  }

  @Test
  void selectsTheArticleInsideMainInsteadOfNavigationArticle() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <title>Company</title>
          <header><article id="gnbWrap">기업소개 투자 재무정보 공고 공시</article></header>
          <main><section><article>
          <div class="viewHead"><h3>2026년 2분기 실적 발표</h3><div class="subjectInfo"><p class="date">2026.08.07</p></div></div>
          <p>2026년 2분기 매출은 1조원으로 증가했고 영업이익은 2천억원을 기록했어요.</p>
          </article></section></main>
          <article id="footerWrap">문의하기</article>
          """);
      assertThat(document.body()).contains("매출은 1조원", "영업이익은 2천억원").doesNotContain("기업소개", "문의하기");
      assertThat(document.publishedOn()).isEqualTo(LocalDate.of(2026, 8, 7));
    }
  }

  @Test
  void skipsHiddenSearchArticleBeforeTheActualNewsArticle() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <article style="display: none;">검색어 삭제 최근 검색어 전체삭제</article>
          <article hidden>숨겨진 메뉴</article>
          <article aria-hidden="true">검색 팝업</article>
          <article><p>3분기 매출액은 23조원이고 영업이익은 전년 대비 증가했다.</p></article>
          """);
      assertThat(document.body())
          .contains("매출액은 23조원", "영업이익은 전년 대비 증가")
          .doesNotContain("검색어", "메뉴", "팝업");
    }
  }

  @Test
  void cancelsSlowResponseAndDoesNotFetchAfterDeadline() throws Exception {
    try (var server = new MockWebServer();
        var client = new PortfolioAnalysisDocumentClient(HttpClients.createDefault())) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "text/html")
              .setBody("<article>Slow</article>")
              .setBodyDelay(1, TimeUnit.SECONDS));
      String url = server.url("/article").toString().replace("127.0.0.1", "localhost");
      assertThat(client.fetch(url, Duration.ofMillis(100))).isEmpty();
      assertThat(client.fetch(url, Duration.ZERO)).isEmpty();
      assertThat(server.getRequestCount()).isEqualTo(1);
    }
  }

  @Test
  void productionClientDoesNotConnectToPrivateHostsOrNonHttpUrls() throws Exception {
    try (var server = new MockWebServer();
        var client = new PortfolioAnalysisDocumentClient()) {
      server.start();
      assertThat(client.fetch(server.url("/").toString(), Duration.ofSeconds(1))).isEmpty();
      assertThat(
              client.fetch(
                  server.url("/").toString().replace("127.0.0.1", "localhost"),
                  Duration.ofSeconds(1)))
          .isEmpty();
      assertThat(client.fetch("file:///etc/passwd", Duration.ofSeconds(1))).isEmpty();
      assertThat(client.fetch("https://user:password@example.com/article", Duration.ofSeconds(1)))
          .isEmpty();
      assertThat(server.getRequestCount()).isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0.0.0.0",
        "10.0.0.1",
        "127.0.0.1",
        "169.254.169.254",
        "172.16.0.1",
        "192.168.1.1",
        "100.64.0.1",
        "198.18.0.1",
        "192.0.2.1",
        "203.0.113.1",
        "224.0.0.1",
        "::1",
        "fc00::1",
        "fe80::1",
        "2001:db8::1",
        "::ffff:127.0.0.1"
      })
  void blocksNonPublicAddresses(String address) throws Exception {
    assertThat(PortfolioAnalysisDocumentClient.isPublicAddress(InetAddress.getByName(address)))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"})
  void acceptsPublicAddresses(String address) throws Exception {
    assertThat(PortfolioAnalysisDocumentClient.isPublicAddress(InetAddress.getByName(address)))
        .isTrue();
  }

  @Test
  void mixedDnsAnswersConnectOnlyToApprovedAddresses() throws Exception {
    var publicAddress = InetAddress.getByName("8.8.8.8");
    var privateAddress = InetAddress.getByName("127.0.0.1");
    var translatedAddress = InetAddress.getByName("64:ff9b::808:808");
    assertThat(
            PortfolioAnalysisDocumentClient.publicAddresses(
                new InetAddress[] {privateAddress, publicAddress, translatedAddress}))
        .containsExactly(publicAddress);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                PortfolioAnalysisDocumentClient.publicAddresses(new InetAddress[] {privateAddress}))
        .isInstanceOf(java.net.UnknownHostException.class);
  }

  @Test
  void readsExplicitArticleHeaderDateWithoutUsingRelatedStoriesOrDateline() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <div class="content_info"><div class="content_meta"><div class="date">2026.07.30</div></div></div>
          <div class="content_desc">SEOUL, Korea – July 30, 2026 – Sales rose.</div>
          <div class="content_others-date">2026.08.05</div>
          """);
      assertThat(document.publishedOn()).isEqualTo(LocalDate.of(2026, 7, 30));
      assertThat(document.body()).contains("Sales rose.").doesNotContain("2026.08.05");
      var old =
          client.parse(
              """
          <article><header class="post-header"><span class="date">October 25, 2022</span></header>
          <div class="post-contents">Seoul, October 26, 2022. Demand fell.</div></article>
          <div class="date">October 20, 2026</div>
          """);
      assertThat(old.publishedOn()).isEqualTo(LocalDate.of(2022, 10, 25));
    }
  }

  @Test
  void equivalentPublicationTimestampsUseSameKoreanDate() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <meta property="article:published_time" content="2026-10-07T23:05:21+00:00">
          <script type="application/ld+json">{"@type":"BlogPosting","datePublished":"2026-10-08T08:05:21+09:00"}</script>
          <div class="content_info"><div class="date">2026.10.08</div></div><article>Facts</article>
          """);
      assertThat(document.publishedOn()).isEqualTo(LocalDate.of(2026, 10, 8));
    }
  }

  @Test
  void readsNewsHeaderDateAndIgnoresRecommendationDates() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var kakao =
          client.parse(
              """
          <article><div class="wrap_title"><span class="text_date">2026.08.06</span></div>Facts</article>
          <div class="item_card"><span class="txt_date">2026.10.23</span></div>
          """);
      assertThat(kakao.publishedOn()).isEqualTo(LocalDate.of(2026, 8, 6));
      var naver =
          client.parse(
              """
          <div class="card-contents__item"><span class="date">2026.09.07</span></div>
          <div class="date media-detail__date">2026.08.07</div><main>Facts</main>
          """);
      assertThat(naver.publishedOn()).isEqualTo(LocalDate.of(2026, 8, 7));
    }
  }

  @Test
  void doesNotNormalizeAnImpossibleHeaderDateToAnotherDay() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      assertThat(
              client
                  .parse(
                      "<div class='content_info'><span class='date'>2026.02.30</span></div><article>Facts</article>")
                  .publishedOn())
          .isNull();
    }
  }

  @Test
  void republicationHeaderDoesNotProveOldEventHappenedAgain() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var document =
          client.parse(
              """
          <title>2026 update</title><article><header class="post-header"><span class="date">October 20, 2026</span></header>
          <p>Seoul, October 26, 2022. Memory demand weakened.</p></article>
          """);
      assertThat(document.publishedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
      assertThat(document.body()).doesNotContain("October 20, 2026");
      assertThat(
              document.rejectionReason(
                  "Memory demand weakened.",
                  document.publishedOn(),
                  LocalDate.of(2026, 10, 20),
                  "2026년 실적이 악화됐어요."))
          .isEqualTo("EVENT_DATE_UNVERIFIED");
      assertThat(
              document.rejectionReason(
                  "Memory demand weakened.", document.publishedOn(), null, "2026년 실적이 악화됐어요."))
          .isEqualTo("PERIOD_UNVERIFIED");
    }
  }

  @Test
  void readsPdfOriginalWithoutTreatingFileCreationTimeAsPublicationDate() throws Exception {
    byte[] bytes;
    try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
        var output = new java.io.ByteArrayOutputStream()) {
      var page = new org.apache.pdfbox.pdmodel.PDPage();
      document.addPage(page);
      document.getDocumentInformation().setCreationDate(java.util.Calendar.getInstance());
      document
          .getDocumentInformation()
          .setCustomMetadataValue("padding", "x".repeat(2 * 1024 * 1024));
      try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(
            new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),
            12);
        content.newLineAtOffset(30, 700);
        content.showText("Revenue rose 12% in Q2 2026.");
        content.endText();
      }
      document.save(output, org.apache.pdfbox.pdfwriter.compress.CompressParameters.NO_COMPRESSION);
      bytes = output.toByteArray();
    }
    assertThat(bytes.length).isGreaterThan(2 * 1024 * 1024).isLessThan(4 * 1024 * 1024);
    try (var server = new MockWebServer();
        var client = new PortfolioAnalysisDocumentClient(HttpClients.createDefault())) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/pdf")
              .setBody(new okio.Buffer().write(bytes)));
      var source =
          client
              .fetch(
                  server.url("/report.pdf").toString().replace("127.0.0.1", "localhost"),
                  Duration.ofSeconds(2))
              .orElseThrow();
      assertThat(source.body()).contains("Revenue rose 12% in Q2 2026.");
      assertThat(source.publishedOn()).isNull();
    }
  }

  @Test
  void readsFinancialPageWithoutArticleOrMainTags() throws Exception {
    try (var client = new PortfolioAnalysisDocumentClient()) {
      var source =
          client.parse(
              """
          <title>Financial filing</title><nav>Other news</nav>
          <div class="filing"><h1>3Q earnings increased 12%.</h1><p>2026 results were announced.</p></div>
          <footer>Old figures</footer>
          """);
      assertThat(source.body())
          .contains("3Q earnings increased 12%.", "2026 results")
          .doesNotContain("Other news", "Old figures");
    }
  }
}
