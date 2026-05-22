package com.wex.challenge.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.repository.PurchaseTransactionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence slice (database layer). Boots only the JPA infrastructure — not the
 * web stack, the Treasury client, caching or resilience — against the in-memory
 * H2 instance defined by the {@code test} profile.
 *
 * <p>{@code replace = NONE} keeps the configured H2 datasource (PostgreSQL mode)
 * instead of swapping in an anonymous embedded one, so Flyway applies the very
 * same {@code V1} migration used in every other environment and Hibernate's
 * {@code ddl-auto: validate} proves the {@link PurchaseTransaction} mapping
 * matches that real schema. This is integration <em>of one layer</em>, not an
 * end-to-end run of the whole application.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PurchaseTransactionRepositoryIT {

    @Autowired
    PurchaseTransactionRepository repository;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistsAndReloadsTransactionFromTheMigratedSchema() {
        PurchaseTransaction saved = repository.save(
                PurchaseTransaction.createNew("Office supplies", LocalDate.of(2025, 12, 1), new BigDecimal("99.99")));
        flushAndClear();

        Optional<PurchaseTransaction> reloaded = repository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getId()).isEqualTo(saved.getId());
        assertThat(reloaded.get().getDescription()).isEqualTo("Office supplies");
        assertThat(reloaded.get().getTransactionDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(reloaded.get().getPurchaseAmount()).isEqualByComparingTo("99.99");
    }

    @Test
    void storesAmountWithTwoDecimalScaleMatchingTheNumericColumn() {
        PurchaseTransaction saved = repository.save(
                PurchaseTransaction.createNew("Coffee", LocalDate.of(2025, 12, 1), new BigDecimal("3.456")));
        flushAndClear();

        BigDecimal stored = repository.findById(saved.getId()).orElseThrow().getPurchaseAmount();

        // Domain rounds to the cent (HALF_UP) and NUMERIC(19,2) preserves that scale.
        assertThat(stored).isEqualByComparingTo("3.46");
        assertThat(stored.scale()).isEqualTo(2);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
