package com.wex.challenge.web;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.service.CurrencyConversionService;
import com.wex.challenge.service.CurrencyConversionService.ConvertedPurchase;
import com.wex.challenge.service.PurchaseTransactionService;
import com.wex.challenge.web.dto.ConvertedPurchaseResponse;
import com.wex.challenge.web.dto.CreatePurchaseTransactionRequest;
import com.wex.challenge.web.dto.PurchaseTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/v1/purchase-transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Purchase Transactions",
        description = "Store purchase transactions and retrieve them in a target currency.")
@Validated
public class PurchaseTransactionController {

    private final PurchaseTransactionService purchaseService;
    private final CurrencyConversionService conversionService;

    public PurchaseTransactionController(PurchaseTransactionService purchaseService,
                                         CurrencyConversionService conversionService) {
        this.purchaseService = purchaseService;
        this.conversionService = conversionService;
    }

    @Operation(summary = "Store a new USD purchase transaction (Requirement #1)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> create(
            @Valid @RequestBody CreatePurchaseTransactionRequest request) {

        PurchaseTransaction created = purchaseService.create(
                request.description(),
                request.transactionDate(),
                request.purchaseAmount());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @Operation(summary = "Get the original USD purchase transaction by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    @GetMapping("/{id}")
    public PurchaseTransactionResponse getById(@PathVariable UUID id) {
        return PurchaseTransactionResponse.from(purchaseService.findById(id));
    }

    @Operation(summary = "Get the stored purchase converted to a target currency (Requirement #2)",
            description = """
                    Looks up the most recent exchange rate from the Treasury Reporting Rates of Exchange
                    API whose record_date is on or before the transaction date and no more than 6 months
                    earlier. Returns the converted amount rounded to two decimal places.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Converted"),
            @ApiResponse(responseCode = "404", description = "Purchase not found"),
            @ApiResponse(responseCode = "422", description = "No exchange rate available within 6 months")
    })
    @GetMapping("/{id}/converted")
    public ConvertedPurchaseResponse getConverted(
            @PathVariable UUID id,
            @Parameter(description = "Target currency as country_currency_desc, e.g. \"Canada-Dollar\".",
                    example = "Canada-Dollar")
            @RequestParam("currency") @NotBlank String currency) {

        ConvertedPurchase result = conversionService.convert(id, currency);
        return ConvertedPurchaseResponse.from(result);
    }
}
