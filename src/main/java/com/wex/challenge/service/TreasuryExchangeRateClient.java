package com.wex.challenge.service;

import com.wex.challenge.exception.TreasuryApiException;
import com.wex.challenge.service.TreasuryRatesApi.TreasuryRateRecord;
import com.wex.challenge.service.TreasuryRatesApi.TreasuryRatesResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class TreasuryExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(TreasuryExchangeRateClient.class);

    private static final String FIELDS = "country_currency_desc,exchange_rate,record_date";
    private static final String SORT = "-record_date";
    private static final String PAGE_SIZE = "1";
    private static final String PAGE_NUMBER = "1";

    private final TreasuryRatesApi api;

    public TreasuryExchangeRateClient(TreasuryRatesApi api) {
        this.api = api;
    }

    @Cacheable(cacheNames = "treasuryRates", sync = true)
    @Retry(name = "treasury")
    @CircuitBreaker(name = "treasury")
    public Optional<ExchangeRate> findLatestRate(String currency, LocalDate purchaseDate, LocalDate windowStart) {
        String filter = "country_currency_desc:eq:%s,record_date:lte:%s,record_date:gte:%s"
                .formatted(currency, purchaseDate, windowStart);

        log.debug("Querying Treasury rates with filter={}", filter);

        TreasuryRatesResponse response;
        try {
            response = api.getRates(FIELDS, filter, SORT, PAGE_SIZE, PAGE_NUMBER);
        } catch (RestClientException e) {
            throw new TreasuryApiException(
                    "Failed to call Treasury Reporting Rates of Exchange API: " + e.getMessage(), e);
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return Optional.empty();
        }

        TreasuryRateRecord rec = response.data().getFirst();
        try {
            return Optional.of(new ExchangeRate(
                    rec.countryCurrencyDesc(),
                    new BigDecimal(rec.exchangeRate()),
                    LocalDate.parse(rec.recordDate())));
        } catch (RuntimeException e) {
            throw new TreasuryApiException("Malformed Treasury response: " + rec, e);
        }
    }
}
