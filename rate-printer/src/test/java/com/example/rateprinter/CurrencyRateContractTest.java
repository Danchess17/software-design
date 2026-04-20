package com.example.rateprinter;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pact consumer test: defines the contract between rate-printer (consumer)
 * and currency-rate-provider (provider) for the GET /api/rate endpoint.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "currency-rate-provider")
public class CurrencyRateContractTest {

    @Pact(consumer = "rate-printer", provider = "currency-rate-provider")
    public V4Pact pact(PactBuilder builder) {
        return builder
                .usingLegacyDsl()
                .given("provider returns USDRUB rate")
                .uponReceiving("a request for USDRUB rate")
                .path("/api/rate")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body("{\"rate\": 85.5}", "application/json")
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(providerName = "currency-rate-provider", pactMethod = "pact")
    void testGetRate(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/api/rate"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"rate\""));
    }
}
