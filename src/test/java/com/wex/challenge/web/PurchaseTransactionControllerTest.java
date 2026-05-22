package com.wex.challenge.web;

import static com.wex.challenge.fixtures.ConvertedPurchaseFixture.aConvertedPurchase;
import static com.wex.challenge.fixtures.PurchaseTransactionFixture.aPurchaseTransaction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.exception.ExchangeRateNotAvailableException;
import com.wex.challenge.exception.PurchaseTransactionNotFoundException;
import com.wex.challenge.exception.TreasuryApiException;
import com.wex.challenge.fixtures.PurchaseTransactionFixture;
import com.wex.challenge.service.CurrencyConversionService;
import com.wex.challenge.service.CurrencyConversionService.ConvertedPurchase;
import com.wex.challenge.service.PurchaseTransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PurchaseTransactionController.class)
@Import(PurchaseTransactionControllerTest.MockedServices.class)
class PurchaseTransactionControllerTest {

    private static final UUID ID = PurchaseTransactionFixture.DEFAULT_ID;
    private static final String CURRENCY = "Canada-Dollar";

    @Autowired
    MockMvc mvc;

    @Autowired
    PurchaseTransactionService purchaseService;

    @Autowired
    CurrencyConversionService conversionService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(purchaseService, conversionService);
    }

    @Test
    void createPersistsAndReturns201WithLocation() throws Exception {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.create("Office supplies", LocalDate.of(2025, 12, 1), new BigDecimal("99.99")))
                .thenReturn(tx);

        mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 99.99
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.purchaseAmount").value(99.99));

        verify(purchaseService).create("Office supplies", LocalDate.of(2025, 12, 1), new BigDecimal("99.99"));
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @Test
    void createRejectsDescriptionOver50Chars() throws Exception {
        mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "%s",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 1.00
                                }
                                """.formatted("x".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void createRejectsNonPositiveAmount() throws Exception {
        mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "x",
                                  "transactionDate": "2025-12-01",
                                  "purchaseAmount": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void createRejectsInvalidDate() throws Exception {
        mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "x",
                                  "transactionDate": "not-a-date",
                                  "purchaseAmount": 1.00
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void createReturns400WhenBodyIsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/purchase-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"));

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void getByIdReturnsStoredTransaction() throws Exception {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);

        mvc.perform(get("/api/v1/purchase-transactions/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.purchaseAmount").value(99.99));

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(purchaseService.findById(ID)).thenThrow(new PurchaseTransactionNotFoundException(ID));

        mvc.perform(get("/api/v1/purchase-transactions/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Purchase transaction not found"));

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @Test
    void getByIdReturns400WhenIdIsNotAUuid() throws Exception {
        mvc.perform(get("/api/v1/purchase-transactions/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"));

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void convertReturnsConvertedPayload() throws Exception {
        ConvertedPurchase result = aConvertedPurchase().build();
        when(conversionService.convert(ID, CURRENCY)).thenReturn(result);

        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", ID)
                        .param("currency", CURRENCY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.originalAmountUsd").value(99.99))
                .andExpect(jsonPath("$.targetCurrency").value(CURRENCY))
                .andExpect(jsonPath("$.exchangeRate").value(1.358))
                .andExpect(jsonPath("$.exchangeRateDate").value("2025-09-30"))
                .andExpect(jsonPath("$.convertedAmount").value(135.79));

        verify(conversionService).convert(ID, CURRENCY);
        verifyNoMoreInteractions(conversionService);
        verifyNoInteractions(purchaseService);
    }

    @Test
    void convertReturns422WhenRateUnavailable() throws Exception {
        when(conversionService.convert(ID, "Atlantis-Gold"))
                .thenThrow(new ExchangeRateNotAvailableException("Atlantis-Gold", LocalDate.of(2025, 12, 1)));

        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", ID)
                        .param("currency", "Atlantis-Gold"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Exchange rate unavailable"))
                .andExpect(jsonPath("$.targetCurrency").value("Atlantis-Gold"));

        verify(conversionService).convert(ID, "Atlantis-Gold");
        verifyNoMoreInteractions(conversionService);
        verifyNoInteractions(purchaseService);
    }

    @Test
    void convertReturns502WhenTreasuryFails() throws Exception {
        when(conversionService.convert(ID, CURRENCY))
                .thenThrow(new TreasuryApiException("upstream blew up", new RuntimeException("boom")));

        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", ID)
                        .param("currency", CURRENCY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Treasury Reporting Rates of Exchange API unavailable"));

        verify(conversionService).convert(ID, CURRENCY);
        verifyNoMoreInteractions(conversionService);
        verifyNoInteractions(purchaseService);
    }

    @Test
    void convertReturns400WhenCurrencyMissing() throws Exception {
        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void convertReturns400WhenCurrencyIsBlank() throws Exception {
        mvc.perform(get("/api/v1/purchase-transactions/{id}/converted", ID)
                        .param("currency", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").exists());

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void unexpectedExceptionsReturn500() throws Exception {
        when(purchaseService.findById(ID)).thenThrow(new RuntimeException("boom"));

        mvc.perform(get("/api/v1/purchase-transactions/{id}", ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @TestConfiguration
    static class MockedServices {
        @Bean
        PurchaseTransactionService purchaseTransactionService() {
            return Mockito.mock(PurchaseTransactionService.class);
        }

        @Bean
        CurrencyConversionService currencyConversionService() {
            return Mockito.mock(CurrencyConversionService.class);
        }
    }
}
