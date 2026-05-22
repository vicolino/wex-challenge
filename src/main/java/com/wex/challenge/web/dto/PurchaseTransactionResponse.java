package com.wex.challenge.web.dto;

import com.wex.challenge.domain.PurchaseTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "PurchaseTransactionResponse",
        description = "Stored purchase transaction in original USD amount.")
public record PurchaseTransactionResponse(
        UUID id,
        String description,
        LocalDate transactionDate,
        BigDecimal purchaseAmount
) {
    public static PurchaseTransactionResponse from(PurchaseTransaction tx) {
        return new PurchaseTransactionResponse(
                tx.getId(),
                tx.getDescription(),
                tx.getTransactionDate(),
                tx.getPurchaseAmount());
    }
}
