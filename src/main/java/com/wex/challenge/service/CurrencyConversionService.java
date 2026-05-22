package com.wex.challenge.service;

import com.wex.challenge.config.TreasuryProperties;
import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.exception.ExchangeRateNotAvailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CurrencyConversionService {

    public static final int CONVERTED_AMOUNT_SCALE = 2;

    private final PurchaseTransactionService purchaseService;
    private final TreasuryExchangeRateClient rateClient;
    private final TreasuryProperties props;

    public CurrencyConversionService(PurchaseTransactionService purchaseService,
                                     TreasuryExchangeRateClient rateClient,
                                     TreasuryProperties props) {
        this.purchaseService = purchaseService;
        this.rateClient = rateClient;
        this.props = props;
    }

    public ConvertedPurchase convert(UUID purchaseId, String currency) {
        PurchaseTransaction tx = purchaseService.findById(purchaseId);
        return convert(tx, currency);
    }

    public ConvertedPurchase convert(PurchaseTransaction tx, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        LocalDate purchaseDate = tx.getTransactionDate();
        LocalDate windowStart = purchaseDate.minusMonths(props.lookbackMonths());

        ExchangeRate rate = rateClient.findLatestRate(currency, purchaseDate, windowStart)
                .orElseThrow(() -> new ExchangeRateNotAvailableException(currency, purchaseDate));

        if (rate.effectiveDate().isAfter(purchaseDate) || rate.effectiveDate().isBefore(windowStart)) {
            throw new ExchangeRateNotAvailableException(currency, purchaseDate);
        }

        BigDecimal converted = tx.getPurchaseAmount()
                .multiply(rate.rate())
                .setScale(CONVERTED_AMOUNT_SCALE, RoundingMode.HALF_UP);

        return new ConvertedPurchase(tx, rate, converted);
    }

    public record ConvertedPurchase(PurchaseTransaction transaction, ExchangeRate rate, BigDecimal convertedAmount) {
    }
}
