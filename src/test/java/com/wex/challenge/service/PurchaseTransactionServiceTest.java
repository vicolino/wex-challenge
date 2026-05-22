package com.wex.challenge.service;

import static com.wex.challenge.fixtures.PurchaseTransactionFixture.aPurchaseTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.wex.challenge.domain.PurchaseTransaction;
import com.wex.challenge.exception.PurchaseTransactionNotFoundException;
import com.wex.challenge.fixtures.PurchaseTransactionFixture;
import com.wex.challenge.repository.PurchaseTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PurchaseTransactionServiceTest {

    private final PurchaseTransactionRepository repository = mock(PurchaseTransactionRepository.class);
    private final PurchaseTransactionService service = new PurchaseTransactionService(repository);

    @Test
    void createPersistsRoundedTransaction() {
        ArgumentCaptor<PurchaseTransaction> captor = ArgumentCaptor.forClass(PurchaseTransaction.class);

        service.create("Coffee", LocalDate.of(2025, 12, 1), new BigDecimal("3.456"));

        verify(repository).save(captor.capture());
        verifyNoMoreInteractions(repository);

        PurchaseTransaction persisted = captor.getValue();
        assertThat(persisted.getDescription()).isEqualTo("Coffee");
        assertThat(persisted.getTransactionDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(persisted.getPurchaseAmount()).isEqualByComparingTo("3.46");
        assertThat(persisted.getId()).isNotNull();
    }

    @Test
    void findByIdReturnsStoredTransaction() {
        PurchaseTransaction stored = aPurchaseTransaction().build();
        UUID id = PurchaseTransactionFixture.DEFAULT_ID;
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        PurchaseTransaction found = service.findById(id);

        assertThat(found).isSameAs(stored);
        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = PurchaseTransactionFixture.DEFAULT_ID;
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(PurchaseTransactionNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(repository).findById(id);
        verifyNoMoreInteractions(repository);
    }
}
