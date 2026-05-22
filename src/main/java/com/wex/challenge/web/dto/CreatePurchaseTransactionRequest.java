package com.wex.challenge.web.dto;

import com.wex.challenge.domain.PurchaseTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreatePurchaseTransactionRequest",
        description = "Payload used to create a new purchase transaction in USD.")
public record CreatePurchaseTransactionRequest(

        @Schema(example = "Office supplies", maxLength = 50)
        @NotBlank
        @Size(max = PurchaseTransaction.MAX_DESCRIPTION_LENGTH,
                message = "description must not exceed {max} characters")
        String description,

        @Schema(example = "2025-12-01", description = "ISO-8601 date (yyyy-MM-dd)")
        @NotNull
        LocalDate transactionDate,

        @Schema(example = "99.99", description = "Positive USD amount; rounded to the nearest cent on storage.")
        @NotNull
        @DecimalMin(value = "0.01", message = "purchaseAmount must be a positive amount")
        @Digits(integer = 17, fraction = 6,
                message = "purchaseAmount may have at most {integer} integer and {fraction} fractional digits")
        BigDecimal purchaseAmount
) {
}
