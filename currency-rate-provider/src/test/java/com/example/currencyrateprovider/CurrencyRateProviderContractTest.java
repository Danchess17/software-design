package com.example.currencyrateprovider;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Pact provider verification: fetches contracts from Pact Broker
 * and verifies this provider's API against them.
 */
@Provider("currency-rate-provider")
@PactBroker(host = "localhost", port = "9292", scheme = "http")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.cloud.zookeeper.ZookeeperAutoConfiguration,org.springframework.cloud.zookeeper.discovery.ZookeeperDiscoveryAutoConfiguration",
        "spring.cloud.zookeeper.enabled=false",
        "spring.cloud.zookeeper.discovery.enabled=false",
        "grpc.server.port=0"
})
public class CurrencyRateProviderContractTest {

    @LocalServerPort
    private int port;

    @MockBean
    private RateService rateService;

    @BeforeEach
    void before(PactVerificationContext context) throws MalformedURLException {
        context.setTarget(HttpTestTarget.fromUrl(new URL("http", "localhost", port, "/")));
    }

    @State("provider returns USDRUB rate")
    void providerReturnsUsdRubRate() {
        when(rateService.getCurrentUsdRubRate()).thenReturn(85.5);
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
