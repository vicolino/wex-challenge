package com.wex.challenge.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.wex.challenge.fixtures.ExchangeRateFixture.DEFAULT_CURRENCY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.wex.challenge.exception.TreasuryApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class TreasuryExchangeRateClientTest {

    private static final String ENDPOINT = "/services/api/fiscal_service/v1/accounting/od/rates_of_exchange";
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2025, 12, 1);
    private static final LocalDate WINDOW_START = LocalDate.of(2025, 6, 1);

    private WireMockServer wireMock;
    private TreasuryExchangeRateClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();
        TreasuryRatesApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(TreasuryRatesApi.class);
        client = new TreasuryExchangeRateClient(api);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void parsesMostRecentRateAndForwardsQueryParameters() {
        stubFor(get(urlPathEqualTo(ENDPOINT))
                .withQueryParam("fields", equalTo("country_currency_desc,exchange_rate,record_date"))
                .withQueryParam("filter", matching(
                        "country_currency_desc:eq:Canada-Dollar,record_date:lte:2025-12-01,record_date:gte:2025-06-01"))
                .withQueryParam("sort", equalTo("-record_date"))
                .withQueryParam("page[size]", equalTo("1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Canada-Dollar",
                                      "exchange_rate": "1.358",
                                      "record_date": "2025-09-30"
                                    }
                                  ]
                                }
                                """)));

        Optional<ExchangeRate> rate = client.findLatestRate(DEFAULT_CURRENCY, PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isPresent();
        assertThat(rate.get().currency()).isEqualTo("Canada-Dollar");
        assertThat(rate.get().rate()).isEqualByComparingTo("1.358");
        assertThat(rate.get().effectiveDate()).isEqualTo(LocalDate.of(2025, 9, 30));

        verify(1, getRequestedFor(urlPathEqualTo(ENDPOINT))
                .withQueryParam("filter", matching(
                        "country_currency_desc:eq:Canada-Dollar,record_date:lte:2025-12-01,record_date:gte:2025-06-01"))
                .withQueryParam("sort", equalTo("-record_date"))
                .withQueryParam("page[size]", equalTo("1")));
    }

    @Test
    void returnsEmptyWhenApiReturnsEmptyDataArray() {
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"data\": [] }")));

        Optional<ExchangeRate> rate = client.findLatestRate("Atlantis-Gold", PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isEmpty();
        verify(1, getRequestedFor(urlPathEqualTo(ENDPOINT)));
    }

    @Test
    void returnsEmptyWhenApiReturnsNullDataField() {
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"meta\": {} }")));

        Optional<ExchangeRate> rate = client.findLatestRate(DEFAULT_CURRENCY, PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isEmpty();
        verify(1, getRequestedFor(urlPathEqualTo(ENDPOINT)));
    }

    @Test
    void returnsEmptyWhenApiReturnsNullJsonBody() {
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("null")));

        Optional<ExchangeRate> rate = client.findLatestRate(DEFAULT_CURRENCY, PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isEmpty();
        verify(1, getRequestedFor(urlPathEqualTo(ENDPOINT)));
    }

    @Test
    void wrapsHttp5xxIntoTreasuryApiException() {
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(serverError()));

        assertThatThrownBy(() -> client.findLatestRate(DEFAULT_CURRENCY, PURCHASE_DATE, WINDOW_START))
                .isInstanceOf(TreasuryApiException.class);

        verify(1, getRequestedFor(urlPathEqualTo(ENDPOINT)));
    }

    @Test
    void wrapsMalformedRateValueIntoTreasuryApiException() {
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "data": [
                            {
                              "country_currency_desc": "Canada-Dollar",
                              "exchange_rate": "not-a-number",
                              "record_date": "2025-09-30"
                            }
                          ]
                        }
                        """)));

        assertThatThrownBy(() -> client.findLatestRate(DEFAULT_CURRENCY, PURCHASE_DATE, WINDOW_START))
                .isInstanceOf(TreasuryApiException.class)
                .hasMessageContaining("Malformed");

        verify(1, getRequestedFor(urlPathEqualTo(ENDPOINT)));
    }
}
