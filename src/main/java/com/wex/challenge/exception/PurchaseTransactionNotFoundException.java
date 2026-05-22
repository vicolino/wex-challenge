package com.wex.challenge.exception;

import java.util.UUID;

public class PurchaseTransactionNotFoundException extends RuntimeException {

    public PurchaseTransactionNotFoundException(UUID id) {
        super("Purchase transaction not found: %s".formatted(id));
    }
}
