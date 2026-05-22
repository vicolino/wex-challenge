package com.wex.challenge.domain;

import static com.wex.challenge.fixtures.PurchaseTransactionFixture.aPurchaseTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseTransactionTest {

    @Test
    void roundsAmountToNearestCentHalfUp() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("12.345").buildViaFactory();
        assertThat(tx.getPurchaseAmount()).isEqualByComparingTo("12.35");
    }

    @Test
    void roundsAmountHalfUpFromExactHalf() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("12.005").buildViaFactory();
        assertThat(tx.getPurchaseAmount()).isEqualByComparingTo("12.01");
    }

    @Test
    void roundsAmountDownWhenBelowHalf() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("12.344").buildViaFactory();
        assertThat(tx.getPurchaseAmount()).isEqualByComparingTo("12.34");
    }

    @Test
    void preservesAlreadyScaledAmount() {
        PurchaseTransaction tx = aPurchaseTransaction().withPurchaseAmount("99.99").buildViaFactory();
        assertThat(tx.getPurchaseAmount()).isEqualByComparingTo("99.99");
    }

    @Test
    void factoryGeneratesAnId() {
        PurchaseTransaction tx = aPurchaseTransaction().buildViaFactory();
        assertThat(tx.getId()).isNotNull();
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> aPurchaseTransaction().withPurchaseAmount(BigDecimal.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> aPurchaseTransaction().withPurchaseAmount("-1.00").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsDescriptionOver50Characters() {
        assertThatThrownBy(() -> aPurchaseTransaction().withDescription("x".repeat(51)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50 characters");
    }

    @Test
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> aPurchaseTransaction().withDescription("   ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullDescription() {
        assertThatThrownBy(() -> aPurchaseTransaction().withDescription(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNullTransactionDate() {
        assertThatThrownBy(() -> aPurchaseTransaction().withTransactionDate(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullPurchaseAmount() {
        assertThatThrownBy(() -> aPurchaseTransaction().withPurchaseAmount((BigDecimal) null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> aPurchaseTransaction().withId(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void allowsDescriptionAtMaxLength() {
        PurchaseTransaction tx = aPurchaseTransaction().withDescription("x".repeat(50)).build();
        assertThat(tx.getDescription()).hasSize(50);
    }

    @Test
    void equalsAndHashCodeUseIdOnly() {
        UUID sharedId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PurchaseTransaction a = aPurchaseTransaction()
                .withId(sharedId).withDescription("a")
                .withTransactionDate(LocalDate.of(2025, 1, 1)).withPurchaseAmount("1.00").build();
        PurchaseTransaction b = aPurchaseTransaction()
                .withId(sharedId).withDescription("b")
                .withTransactionDate(LocalDate.of(2025, 2, 1)).withPurchaseAmount("2.00").build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void equalsReturnsTrueForSameInstance() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        assertThat(tx.equals(tx)).isTrue();
    }

    @Test
    void equalsReturnsFalseForNullAndOtherType() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        assertThat(tx.equals(null)).isFalse();
        assertThat(tx.equals("not a transaction")).isFalse();
    }

    @Test
    void toStringIncludesAllFields() {
        PurchaseTransaction tx = aPurchaseTransaction().build();
        assertThat(tx.toString())
                .contains(tx.getId().toString())
                .contains("Office supplies")
                .contains("2025-12-01")
                .contains("99.99");
    }
}
