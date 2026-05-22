package com.wex.challenge.fixtures;

import com.wex.challenge.service.ExchangeRate;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ExchangeRateFixture {

    public static final String DEFAULT_CURRENCY = "Canada-Dollar";
    public static final BigDecimal DEFAULT_RATE = new BigDecimal("1.358");
    public static final LocalDate DEFAULT_EFFECTIVE_DATE = LocalDate.of(2025, 9, 30);

    private String currency = DEFAULT_CURRENCY;
    private BigDecimal rate = DEFAULT_RATE;
    private LocalDate effectiveDate = DEFAULT_EFFECTIVE_DATE;

    private ExchangeRateFixture() {
    }

    public static ExchangeRateFixture anExchangeRate() {
        return new ExchangeRateFixture();
    }

    public ExchangeRateFixture withCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public ExchangeRateFixture withRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public ExchangeRateFixture withRate(String rate) {
        this.rate = new BigDecimal(rate);
        return this;
    }

    public ExchangeRateFixture withEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
        return this;
    }

    public ExchangeRate build() {
        return new ExchangeRate(currency, rate, effectiveDate);
    }
}
