package com.wex.challenge.web;

import static com.wex.challenge.fixtures.ConvertedPurchaseFixture.aConvertedPurchase;
import static com.wex.challenge.fixtures.PurchaseTransactionFixture.aPurchaseTransaction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.exception.ExchangeRateNotAvailableException;
import com.wex.challenge.exception.PurchaseTransactionNotFoundException;
import com.wex.challenge.exception.TreasuryApiException;
import com.wex.challenge.fixtures.PurchaseTransactionFixture;
import com.wex.challenge.service.CurrencyConversionService;
import com.wex.challenge.service.CurrencyConversionService.ConvertedPurchase;
import com.wex.challenge.service.PurchaseTransactionService;
import com.wex.challenge.web.dto.CreatePurchaseTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Web-layer integration slice. Boots only the MVC stack for
 * {@link PurchaseTransactionController} ({@code @WebMvcTest}) — routing,
 * validation, content negotiation, serialization and the RFC 7807 error model —
 * with the service collaborators mocked. Requests are driven through
 * {@link RestTestClient} bound to the auto-configured {@code MockMvc}.
 */
@WebMvcTest(PurchaseTransactionController.class)
class PurchaseTransactionControllerIT extends WebTestBase {

    private static final UUID ID = PurchaseTransactionFixture.DEFAULT_ID;
    private static final String CURRENCY = "Canada-Dollar";

    @MockitoBean
    private PurchaseTransactionService purchaseService;

    @MockitoBean
    private CurrencyConversionService conversionService;

    // --- POST /api/v1/purchase-transactions ---------------------------------

    @Test
    void createPurchase_validRequest_returns201WithLocationAndEmptyBody() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.create("Office supplies", LocalDate.of(2025, 12, 1), new BigDecimal("99.99")))
                .thenReturn(tx);

        RestTestClient.bindTo(mockMvc).build()
                .post().uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreatePurchaseTransactionRequest("Office supplies", LocalDate.of(2025, 12, 1),
                        new BigDecimal("99.99")))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/v1/purchase-transactions/" + ID)
                .expectBody().isEmpty();

        verify(purchaseService).create("Office supplies", LocalDate.of(2025, 12, 1), new BigDecimal("99.99"));
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @Test
    void createPurchase_descriptionOver50Chars_returns400WithValidationError() {
        RestTestClient.bindTo(mockMvc).build()
                .post().uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreatePurchaseTransactionRequest("x".repeat(51), LocalDate.of(2025, 12, 1),
                        new BigDecimal("1.00")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.title").isEqualTo("Validation failed");

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void createPurchase_nonPositiveAmount_returns400() {
        RestTestClient.bindTo(mockMvc).build()
                .post().uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreatePurchaseTransactionRequest("x", LocalDate.of(2025, 12, 1), BigDecimal.ZERO))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void createPurchase_invalidDate_returns400() {
        RestTestClient.bindTo(mockMvc).build()
                .post().uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "description": "x",
                          "transactionDate": "not-a-date",
                          "purchaseAmount": 1.00
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void createPurchase_malformedJson_returns400() {
        RestTestClient.bindTo(mockMvc).build()
                .post().uri("/api/v1/purchase-transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ not valid json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.title").isEqualTo("Bad request");

        verifyNoInteractions(purchaseService, conversionService);
    }

    // --- GET /api/v1/purchase-transactions/{id} -----------------------------

    @Test
    void getById_existingId_returns200WithTransaction() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);

        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}", ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(ID.toString())
                .jsonPath("$.description").isEqualTo("Office supplies")
                .jsonPath("$.purchaseAmount").isEqualTo(99.99);

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @Test
    void getById_unknownId_returns404() {
        when(purchaseService.findById(ID)).thenThrow(new PurchaseTransactionNotFoundException(ID));

        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}", ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.title").isEqualTo("Purchase transaction not found");

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    @Test
    void getById_nonUuidId_returns400() {
        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}", "not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.title").isEqualTo("Bad request");

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void getById_serviceThrowsRuntimeException_returns500() {
        when(purchaseService.findById(ID)).thenThrow(new RuntimeException("boom"));

        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}", ID)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Internal server error")
                .jsonPath("$.detail").isEqualTo("An unexpected error occurred");

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(conversionService);
    }

    // --- GET /api/v1/purchase-transactions/{id}/converted -------------------

    @Test
    void convert_validRequest_returns200WithConvertedPayload() {
        ConvertedPurchase result = aConvertedPurchase().build();
        when(conversionService.convert(ID, CURRENCY)).thenReturn(result);

        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}/converted?currency={currency}", ID, CURRENCY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(ID.toString())
                .jsonPath("$.originalAmountUsd").isEqualTo(99.99)
                .jsonPath("$.targetCurrency").isEqualTo(CURRENCY)
                .jsonPath("$.exchangeRate").isEqualTo(1.358)
                .jsonPath("$.exchangeRateDate").isEqualTo("2025-09-30")
                .jsonPath("$.convertedAmount").isEqualTo(135.79);

        verify(conversionService).convert(ID, CURRENCY);
        verifyNoMoreInteractions(conversionService);
        verifyNoInteractions(purchaseService);
    }

    @Test
    void convert_noRateInWindow_returns422() {
        when(conversionService.convert(ID, "Atlantis-Gold"))
                .thenThrow(new ExchangeRateNotAvailableException("Atlantis-Gold", LocalDate.of(2025, 12, 1)));

        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}/converted?currency={currency}", ID, "Atlantis-Gold")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Exchange rate unavailable")
                .jsonPath("$.targetCurrency").isEqualTo("Atlantis-Gold");

        verify(conversionService).convert(ID, "Atlantis-Gold");
        verifyNoMoreInteractions(conversionService);
        verifyNoInteractions(purchaseService);
    }

    @Test
    void convert_treasuryFails_returns502() {
        when(conversionService.convert(ID, CURRENCY))
                .thenThrow(new TreasuryApiException("upstream blew up", new RuntimeException("boom")));

        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}/converted?currency={currency}", ID, CURRENCY)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Treasury Reporting Rates of Exchange API unavailable");

        verify(conversionService).convert(ID, CURRENCY);
        verifyNoMoreInteractions(conversionService);
        verifyNoInteractions(purchaseService);
    }

    @Test
    void convert_missingCurrency_returns400() {
        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}/converted", ID)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(purchaseService, conversionService);
    }

    @Test
    void convert_blankCurrency_returns400WithValidationError() {
        RestTestClient.bindTo(mockMvc).build()
                .get().uri("/api/v1/purchase-transactions/{id}/converted?currency={currency}", ID, "  ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation failed")
                .jsonPath("$.errors[0].field").exists();

        verifyNoInteractions(purchaseService, conversionService);
    }
}
