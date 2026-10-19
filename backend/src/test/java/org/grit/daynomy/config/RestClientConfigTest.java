package org.grit.daynomy.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class RestClientConfigTest {

  @Test
  void createsObservationsForOutgoingHttpRequests() throws Exception {
    var stoppedObservation = new AtomicReference<String>();
    var registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<Observation.Context>() {
              @Override
              public void onStop(Observation.Context context) {
                stoppedObservation.set(context.getName());
              }

              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }
            });
    try (var server = new MockWebServer()) {
      server.enqueue(new MockResponse().setBody("ok"));
      var client =
          new RestClientConfig()
              .restClientBuilder(registry)
              .baseUrl(server.url("/").toString())
              .build();

      assertThat(client.get().retrieve().body(String.class)).isEqualTo("ok");

      assertThat(stoppedObservation).hasValue("http.client.requests");
    }
  }
}
