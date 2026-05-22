package com.wex.challenge.fixtures;

import com.wex.challenge.domain.PurchaseTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class PurchaseTransactionFixture {

    public static final UUID DEFAULT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String DEFAULT_DESCRIPTION = "Office supplies";
    public static final LocalDate DEFAULT_TRANSACTION_DATE = LocalDate.of(2025, 12, 1);
    public static final BigDecimal DEFAULT_PURCHASE_AMOUNT = new BigDecimal("99.99");

    private UUID id = DEFAULT_ID;
    private String description = DEFAULT_DESCRIPTION;
    private LocalDate transactionDate = DEFAULT_TRANSACTION_DATE;
    private BigDecimal purchaseAmount = DEFAULT_PURCHASE_AMOUNT;

    private PurchaseTransactionFixture() {
    }

    public static PurchaseTransactionFixture aPurchaseTransaction() {
        return new PurchaseTransactionFixture();
    }

    public PurchaseTransactionFixture withId(UUID id) {
        this.id = id;
        return this;
    }

    public PurchaseTransactionFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public PurchaseTransactionFixture withTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
        return this;
    }

    public PurchaseTransactionFixture withPurchaseAmount(BigDecimal purchaseAmount) {
        this.purchaseAmount = purchaseAmount;
        return this;
    }

    public PurchaseTransactionFixture withPurchaseAmount(String purchaseAmount) {
        this.purchaseAmount = new BigDecimal(purchaseAmount);
        return this;
    }

    public PurchaseTransaction build() {
        return new PurchaseTransaction(id, description, transactionDate, purchaseAmount);
    }

    public PurchaseTransaction buildViaFactory() {
        return PurchaseTransaction.createNew(description, transactionDate, purchaseAmount);
    }
}
