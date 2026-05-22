package com.wex.challenge.repository;

import com.wex.challenge.domain.PurchaseTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseTransactionRepository extends JpaRepository<PurchaseTransaction, UUID> {
}
