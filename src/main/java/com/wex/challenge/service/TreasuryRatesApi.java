package com.wex.challenge.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface TreasuryRatesApi {

    @GetExchange("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange")
    TreasuryRatesResponse getRates(
            @RequestParam("fields") String fields,
            @RequestParam("filter") String filter,
            @RequestParam("sort") String sort,
            @RequestParam("page[size]") String pageSize,
            @RequestParam("page[number]") String pageNumber);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreasuryRatesResponse(List<TreasuryRateRecord> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TreasuryRateRecord(
            @JsonProperty("country_currency_desc") String countryCurrencyDesc,
            @JsonProperty("exchange_rate") String exchangeRate,
            @JsonProperty("record_date") String recordDate) {
    }
}
