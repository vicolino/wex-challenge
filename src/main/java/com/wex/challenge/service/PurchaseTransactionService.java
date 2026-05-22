package com.wex.challenge.service;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.exception.PurchaseTransactionNotFoundException;
import com.wex.challenge.repository.PurchaseTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseTransactionService {

    private final PurchaseTransactionRepository repository;

    public PurchaseTransactionService(PurchaseTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PurchaseTransaction create(String description, LocalDate transactionDate, BigDecimal purchaseAmount) {
        PurchaseTransaction tx = PurchaseTransaction.createNew(description, transactionDate, purchaseAmount);
        return repository.save(tx);
    }

    @Transactional(readOnly = true)
    public PurchaseTransaction findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PurchaseTransactionNotFoundException(id));
    }
}
