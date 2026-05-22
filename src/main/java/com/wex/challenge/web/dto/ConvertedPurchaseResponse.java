package com.wex.challenge.web.dto;

import com.wex.challenge.service.CurrencyConversionService.ConvertedPurchase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "ConvertedPurchaseResponse",
        description = "Purchase transaction converted to a target currency.")
public record ConvertedPurchaseResponse(
        UUID id,
        String description,
        LocalDate transactionDate,
        @Schema(description = "Original USD purchase amount, rounded to the nearest cent.")
        BigDecimal originalAmountUsd,
        @Schema(description = "Target currency, as country_currency_desc (e.g. \"Canada-Dollar\").")
        String targetCurrency,
        @Schema(description = "Exchange rate used (foreign units per US dollar).")
        BigDecimal exchangeRate,
        @Schema(description = "record_date of the rate used; <= transactionDate and within 6 months.")
        LocalDate exchangeRateDate,
        @Schema(description = "Converted amount in target currency, rounded to two decimal places.")
        BigDecimal convertedAmount
) {
    public static ConvertedPurchaseResponse from(ConvertedPurchase result) {
        var tx = result.transaction();
        var rate = result.rate();
        return new ConvertedPurchaseResponse(
                tx.getId(),
                tx.getDescription(),
                tx.getTransactionDate(),
                tx.getPurchaseAmount(),
                rate.currency(),
                rate.rate(),
                rate.effectiveDate(),
                result.convertedAmount());
    }
}
