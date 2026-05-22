package com.wex.challenge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wex.challenge.service.TreasuryRatesApi;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Unit test for the Treasury {@link RestClient} wiring. We exercise the bean
 * factory methods directly (no Spring context) so the SSL/truststore setup —
 * loading the JVM's default {@code cacerts} and grafting the bundled Sectigo
 * root onto it — is covered without booting the whole application.
 */
class RestClientConfigTest {

    private static final TreasuryProperties PROPS =
            new TreasuryProperties("https://api.fiscaldata.treasury.gov", 5000, 10000, 6);

    private final RestClientConfig config = new RestClientConfig();

    @Test
    void buildsTreasuryRestClientWithBundledTrustStore() throws Exception {
        RestClient restClient = config.treasuryRestClient(PROPS);

        assertThat(restClient).isNotNull();
    }

    @Test
    void buildsTreasuryRatesApiProxyOverTheRestClient() throws Exception {
        RestClient restClient = config.treasuryRestClient(PROPS);

        TreasuryRatesApi api = config.treasuryRatesApi(restClient);

        assertThat(api).isNotNull();
    }
}
