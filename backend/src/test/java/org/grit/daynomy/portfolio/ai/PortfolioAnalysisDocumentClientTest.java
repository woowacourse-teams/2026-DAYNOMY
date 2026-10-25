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
  void fetchesHtmlButDoesNotFollowRedirectOrAcceptErrorsAndLargeBodies() throws Exception {
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
      server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/private"));
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
