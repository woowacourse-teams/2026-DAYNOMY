package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiImageGeneratorTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("OpenAI Images API 응답에서 이미지 data URL을 만든다")
  void generateNewsImageReturnsDataUrl() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties(
                "test-key", startServer(openAiImageResponse()), "text-model", "image-model"));

    String imageUrl = generator.generateNewsImage("제목", "요약");

    assertThat(imageUrl).isEqualTo("data:image/webp;base64,test-image");
  }

  private String startServer(String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/images/generations",
        exchange -> {
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private String openAiImageResponse() {
    return """
        {
          "data": [
            {
              "b64_json": "test-image"
            }
          ]
        }
        """;
  }
}
