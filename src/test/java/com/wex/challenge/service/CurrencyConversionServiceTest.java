package com.wex.challenge.service;

import static com.wex.challenge.fixtures.ExchangeRateFixture.anExchangeRate;
import static com.wex.challenge.fixtures.PurchaseTransactionFixture.aPurchaseTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.wex.challenge.config.TreasuryProperties;
import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.exception.ExchangeRateNotAvailableException;
import com.wex.challenge.fixtures.PurchaseTransactionFixture;
import com.wex.challenge.service.CurrencyConversionService.ConvertedPurchase;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    private static final String CURRENCY = "Canada-Dollar";
    private static final UUID ID = PurchaseTransactionFixture.DEFAULT_ID;
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2025, 12, 1);
    private static final LocalDate WINDOW_START = LocalDate.of(2025, 6, 1);

    @Mock
    private PurchaseTransactionService purchaseService;
    @Mock
    private TreasuryExchangeRateClient rateClient;

    private CurrencyConversionService service;
    private final TreasuryProperties props = new TreasuryProperties(
            "http://localhost", 1000, 1000, 6);

    @BeforeEach
    void setUp() {
        service = new CurrencyConversionService(purchaseService, rateClient, props);
    }

    @Test
    void convertsAmountUsingExchangeRateAndRoundsHalfUp() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("99.99").build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START))
                .thenReturn(Optional.of(anExchangeRate().withRate("1.358").build()));

        ConvertedPurchase result = service.convert(ID, CURRENCY);

        assertThat(result.convertedAmount()).isEqualByComparingTo("135.79");
        assertThat(result.rate().rate()).isEqualByComparingTo("1.358");
        assertThat(result.transaction()).isSameAs(tx);

        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }

    @Test
    void roundsHalfUpAtExactHalfBoundary() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("10.00").build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START))
                .thenReturn(Optional.of(anExchangeRate().withRate("0.125").build()));

        ConvertedPurchase result = service.convert(ID, CURRENCY);

        assertThat(result.convertedAmount()).isEqualByComparingTo("1.25");
        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }

    @Test
    void roundsHalfUpWhenThirdDecimalIsFive() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("1.00").build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START))
                .thenReturn(Optional.of(anExchangeRate().withRate("0.005").build()));

        ConvertedPurchase result = service.convert(ID, CURRENCY);

        assertThat(result.convertedAmount()).isEqualByComparingTo("0.01");
        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }

    @Test
    void usesPurchaseDateMinusSixMonthsAsWindowStart() {
        LocalDate purchaseDate = LocalDate.of(2025, 12, 15);
        LocalDate windowStart = LocalDate.of(2025, 6, 15);
        PurchaseTransaction tx = aPurchaseTransaction().withTransactionDate(purchaseDate).build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, purchaseDate, windowStart))
                .thenReturn(Optional.of(anExchangeRate().withEffectiveDate(windowStart).build()));

        ConvertedPurchase result = service.convert(ID, CURRENCY);

        assertThat(result.rate().effectiveDate()).isEqualTo(windowStart);
        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, purchaseDate, windowStart);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }

    @Test
    void throwsWhenNoRateInWindow() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convert(ID, CURRENCY))
                .isInstanceOf(ExchangeRateNotAvailableException.class)
                .hasMessageContaining(CURRENCY)
                .hasMessageContaining("6 months");

        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }

    @Test
    void rejectsBlankCurrency() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);

        assertThatThrownBy(() -> service.convert(ID, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(rateClient);
    }

    @Test
    void rejectsNullCurrency() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);

        assertThatThrownBy(() -> service.convert(ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(purchaseService).findById(ID);
        verifyNoMoreInteractions(purchaseService);
        verifyNoInteractions(rateClient);
    }

    @Test
    void defensiveRejectionWhenRateIsAfterPurchaseDate() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START))
                .thenReturn(Optional.of(anExchangeRate().withEffectiveDate(PURCHASE_DATE.plusDays(1)).build()));

        assertThatThrownBy(() -> service.convert(ID, CURRENCY))
                .isInstanceOf(ExchangeRateNotAvailableException.class);

        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }

    @Test
    void defensiveRejectionWhenRateIsBeforeWindowStart() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        when(purchaseService.findById(ID)).thenReturn(tx);
        when(rateClient.findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START))
                .thenReturn(Optional.of(anExchangeRate().withEffectiveDate(WINDOW_START.minusDays(1)).build()));

        assertThatThrownBy(() -> service.convert(ID, CURRENCY))
                .isInstanceOf(ExchangeRateNotAvailableException.class);

        verify(purchaseService).findById(ID);
        verify(rateClient).findLatestRate(CURRENCY, PURCHASE_DATE, WINDOW_START);
        verifyNoMoreInteractions(purchaseService, rateClient);
    }
}
