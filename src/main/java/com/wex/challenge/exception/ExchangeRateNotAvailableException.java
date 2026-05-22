package com.wex.challenge.exception;

import java.time.LocalDate;

public class ExchangeRateNotAvailableException extends RuntimeException {

    private final String currency;
    private final LocalDate purchaseDate;

    public ExchangeRateNotAvailableException(String currency, LocalDate purchaseDate) {
        super(("The purchase cannot be converted to the target currency: no exchange rate"
                + " available for '%s' within 6 months on or before %s")
                .formatted(currency, purchaseDate));
        this.currency = currency;
        this.purchaseDate = purchaseDate;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }
}
