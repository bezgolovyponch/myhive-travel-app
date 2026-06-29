package com.myhive.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentCalculatorTest {

    @Test
    void toCents_roundsHalfUp() {
        assertThat(PaymentCalculator.toCents(new BigDecimal("199.98"))).isEqualTo(19998L);
        assertThat(PaymentCalculator.toCents(new BigDecimal("100.00"))).isEqualTo(10000L);
    }

    @Test
    void depositAndBalance_sumToTotal() {
        long total = 10000L; // €100.00
        long expectedDeposit = 3000L; // 30%
        long expectedBalance = 7000L;

        assertThat(PaymentCalculator.depositCents(total, 30)).isEqualTo(expectedDeposit);
        assertThat(PaymentCalculator.balanceCents(total, 30)).isEqualTo(expectedBalance);
        assertThat(PaymentCalculator.depositCents(total, 30) + PaymentCalculator.balanceCents(total, 30))
                .isEqualTo(total);
    }

    @Test
    void depositRoundsHalfUp_andBalanceAbsorbsRemainder() {
        long total = 10001L; // €100.01 → 30% = 3000.3 → 3000
        assertThat(PaymentCalculator.depositCents(total, 30)).isEqualTo(3000L);
        assertThat(PaymentCalculator.balanceCents(total, 30)).isEqualTo(7001L);
    }

    @Test
    void splitEqually_dividesEvenly() {
        assertThat(PaymentCalculator.splitEqually(9000L, 3)).containsExactly(3000L, 3000L, 3000L);
    }

    @Test
    void splitEqually_frontLoadsRemainder_andSumsExactly() {
        List<Long> shares = PaymentCalculator.splitEqually(7000L, 3); // 2333.33 → 2334,2333,2333
        assertThat(shares).containsExactly(2334L, 2333L, 2333L);
        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(7000L);
    }

    @Test
    void splitEqually_rejectsNonPositiveN() {
        assertThatThrownBy(() -> PaymentCalculator.splitEqually(100L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
