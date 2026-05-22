package com.wex.challenge.fixtures;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.service.CurrencyConversionService.ConvertedPurchase;
import com.wex.challenge.service.ExchangeRate;
import java.math.BigDecimal;

public final class ConvertedPurchaseFixture {

    public static final BigDecimal DEFAULT_CONVERTED_AMOUNT = new BigDecimal("135.79");

    private PurchaseTransaction transaction = PurchaseTransactionFixture.aPurchaseTransaction().build();
    private ExchangeRate rate = ExchangeRateFixture.anExchangeRate().build();
    private BigDecimal convertedAmount = DEFAULT_CONVERTED_AMOUNT;

    private ConvertedPurchaseFixture() {
    }

    public static ConvertedPurchaseFixture aConvertedPurchase() {
        return new ConvertedPurchaseFixture();
    }

    public ConvertedPurchaseFixture withTransaction(PurchaseTransaction transaction) {
        this.transaction = transaction;
        return this;
    }

    public ConvertedPurchaseFixture withRate(ExchangeRate rate) {
        this.rate = rate;
        return this;
    }

    public ConvertedPurchaseFixture withConvertedAmount(BigDecimal convertedAmount) {
        this.convertedAmount = convertedAmount;
        return this;
    }

    public ConvertedPurchase build() {
        return new ConvertedPurchase(transaction, rate, convertedAmount);
    }
}
