package com.wex.challenge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "purchase_transactions")
public class PurchaseTransaction {

    public static final int MAX_DESCRIPTION_LENGTH = 50;
    public static final int AMOUNT_SCALE = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "description", nullable = false, length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "purchase_amount", nullable = false, precision = 19, scale = AMOUNT_SCALE)
    private BigDecimal purchaseAmount;

    protected PurchaseTransaction() {
    }

    public PurchaseTransaction(UUID id, String description, LocalDate transactionDate, BigDecimal purchaseAmount) {
        this.id = Objects.requireNonNull(id, "id");
        setDescription(description);
        setTransactionDate(transactionDate);
        setPurchaseAmount(purchaseAmount);
    }

    public static PurchaseTransaction createNew(String description, LocalDate transactionDate, BigDecimal purchaseAmount) {
        return new PurchaseTransaction(UUID.randomUUID(), description, transactionDate, purchaseAmount);
    }

    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public BigDecimal getPurchaseAmount() {
        return purchaseAmount;
    }

    private void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must not exceed %d characters"
                    .formatted(MAX_DESCRIPTION_LENGTH));
        }
        this.description = description;
    }

    private void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = Objects.requireNonNull(transactionDate, "transactionDate");
    }

    private void setPurchaseAmount(BigDecimal purchaseAmount) {
        Objects.requireNonNull(purchaseAmount, "purchaseAmount");
        if (purchaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("purchaseAmount must be a positive amount");
        }
        this.purchaseAmount = purchaseAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseTransaction other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PurchaseTransaction[id=%s, description=%s, transactionDate=%s, purchaseAmount=%s]"
                .formatted(id, description, transactionDate, purchaseAmount);
    }
}
