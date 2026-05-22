package com.wex.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.wex.challenge.exception.TreasuryApiException;
import com.wex.challenge.service.TreasuryRatesApi.TreasuryRateRecord;
import com.wex.challenge.service.TreasuryRatesApi.TreasuryRatesResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Client-layer integration slice for {@link TreasuryExchangeRateClient}. Stubs
 * the Treasury HTTP exchange with {@link org.springframework.test.web.client.MockRestServiceServer}
 * (via {@link HttpExchangeClientTestBase}) and asserts the client's URL/query
 * building, JSON parsing and error translation.
 */
class TreasuryExchangeRateClientIT extends HttpExchangeClientTestBase {

    private static final String ENDPOINT = "/services/api/fiscal_service/v1/accounting/od/rates_of_exchange";
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2025, 12, 1);
    private static final LocalDate WINDOW_START = LocalDate.of(2025, 6, 1);

    private TreasuryExchangeRateClient client;

    @BeforeEach
    void setUp() {
        client = new TreasuryExchangeRateClient(buildClient(TreasuryRatesApi.class));
    }

    @Test
    @DisplayName("Parses the most recent rate from a 200 response")
    void should_parseMostRecentRate_when_200WithData() {
        mockGet(ENDPOINT, new TreasuryRatesResponse(
                List.of(new TreasuryRateRecord("Canada-Dollar", "1.358", "2025-09-30"))));

        Optional<ExchangeRate> rate = client.findLatestRate("Canada-Dollar", PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isPresent();
        assertThat(rate.get().currency()).isEqualTo("Canada-Dollar");
        assertThat(rate.get().rate()).isEqualByComparingTo("1.358");
        assertThat(rate.get().effectiveDate()).isEqualTo(LocalDate.of(2025, 9, 30));
        server.verify();
    }

    @Test
    @DisplayName("Returns empty when the data array is empty")
    void should_returnEmpty_when_dataArrayIsEmpty() {
        mockGet(ENDPOINT, new TreasuryRatesResponse(List.of()));

        Optional<ExchangeRate> rate = client.findLatestRate("Atlantis-Gold", PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Returns empty when the data field is null")
    void should_returnEmpty_when_dataFieldIsNull() {
        mockGet(ENDPOINT, new TreasuryRatesResponse(null));

        Optional<ExchangeRate> rate = client.findLatestRate("Canada-Dollar", PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Returns empty when the JSON body is literally null")
    void should_returnEmpty_when_jsonBodyIsNull() {
        server.expect(requestTo(containsString(ENDPOINT)))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        Optional<ExchangeRate> rate = client.findLatestRate("Canada-Dollar", PURCHASE_DATE, WINDOW_START);

        assertThat(rate).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Wraps HTTP 5xx into TreasuryApiException")
    void should_throwTreasuryApiException_when_5xxResponse() {
        mockGet(ENDPOINT, HttpStatus.INTERNAL_SERVER_ERROR);

        assertThatThrownBy(() -> client.findLatestRate("Canada-Dollar", PURCHASE_DATE, WINDOW_START))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    @DisplayName("Wraps a malformed rate value into TreasuryApiException")
    void should_throwTreasuryApiException_when_rateValueIsMalformed() {
        mockGet(ENDPOINT, new TreasuryRatesResponse(
                List.of(new TreasuryRateRecord("Canada-Dollar", "not-a-number", "2025-09-30"))));

        assertThatThrownBy(() -> client.findLatestRate("Canada-Dollar", PURCHASE_DATE, WINDOW_START))
                .isInstanceOf(TreasuryApiException.class)
                .hasMessageContaining("Malformed");
    }
}
