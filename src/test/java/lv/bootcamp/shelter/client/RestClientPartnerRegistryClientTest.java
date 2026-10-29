package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientPartnerRegistryClientTest {

    private MockWebServer server;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new RestClientPartnerRegistryClient(RestClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // The real HTTP client reads JSON and calls the correct registry endpoint.
    @Test
    void lookup_shouldReturnPartnerRegistryResult() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"registered": true, "partnerShelterName": "Riga Shelter"}
                        """));

        PartnerRegistryResult result = client.lookup("CHIP-001");

        assertThat(result).isEqualTo(new PartnerRegistryResult(true, "Riga Shelter"));
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-001");
    }

    // The current client propagates HTTP 500 as a Spring exception.
    @Test
    void lookup_shouldThrowWhenPartnerRegistryFails() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Registry unavailable"));

        assertThatThrownBy(() -> client.lookup("CHIP-002"))
                .isInstanceOf(HttpServerErrorException.InternalServerError.class)
                .satisfies(error -> {
                    HttpServerErrorException responseError = (HttpServerErrorException) error;
                    assertThat(responseError.getStatusCode().value()).isEqualTo(500);
                    assertThat(responseError.getResponseBodyAsString()).isEqualTo("Registry unavailable");
                });

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-002");
    }
}
