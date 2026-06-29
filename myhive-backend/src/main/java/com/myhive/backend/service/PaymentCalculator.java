package com.myhive.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure money math for the deposit/split payment flow. Works in integer cents to
 * match Stripe's API and to guarantee shares sum back to the source amount with no
 * rounding drift. No Stripe and no persistence here — trivially unit-testable.
 */
public final class PaymentCalculator {

    private PaymentCalculator() {
    }

    public static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long depositCents(long totalCents, int depositPct) {
        return BigDecimal.valueOf(totalCents)
                .multiply(BigDecimal.valueOf(depositPct))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long balanceCents(long totalCents, int depositPct) {
        return totalCents - depositCents(totalCents, depositPct);
    }

    public static List<Long> splitEqually(long amountCents, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("Number of shares must be at least 1, was " + n);
        }
        if (amountCents < 0) {
            // L2: integer division truncates toward zero, so the remainder front-load breaks
            // sum-invariance for negative amounts. The contract only holds for amountCents >= 0.
            throw new IllegalArgumentException("Amount to split must be non-negative, was " + amountCents);
        }
        long base = amountCents / n;
        long remainder = amountCents - base * n; // 0..n-1 cents to distribute
        List<Long> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            shares.add(i < remainder ? base + 1 : base);
        }
        return shares;
    }
}
