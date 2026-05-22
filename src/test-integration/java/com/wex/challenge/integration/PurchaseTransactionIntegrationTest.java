package com.wex.challenge.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseTransactionIntegrationTest {

    private static WireMockServer wireMock;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CacheManager cacheManager;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @DynamicPropertySource
    static void overrideTreasuryBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("treasury.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Test
    void createThenConvertHappyPath() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 99.99
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        String id = body.get("id").asString();

        stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
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

        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.originalAmountUsd").value(99.99))
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.exchangeRate").value(1.358))
                .andExpect(jsonPath("$.exchangeRateDate").value("2025-09-30"))
                .andExpect(jsonPath("$.convertedAmount").value(135.79));

        wireMock.verify(WireMock.getRequestedFor(
                urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")));
    }

    @Test
    void convertReturns422WhenNoRateInWindow() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Atlantis trip",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 50.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asString();

        stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"data\": [] }")));

        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", id)
                        .param("currency", "Atlantis-Gold"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Exchange rate unavailable"));
    }

    @Test
    void convertReturns502WhenTreasuryReturns5xx() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "x",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 1.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asString();

        stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse().withStatus(503)));

        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void purchaseAmountIsRoundedOnStore() throws Exception {
        mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Coffee",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 3.456
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmount").value(3.46));
    }

    @Test
    void contextLoads() {
    }
}
