package com.myhive.backend.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The most the organizer wants to spend, in euros, as they said it: per person or for the whole group. Every
 * package is held to it on the price the plan shows - the "from" price per person. A non-positive amount is
 * "no limit": the chat sends 0 when the organizer drops the budget, and that clears it.
 */
public record BudgetLimit(@DecimalMin("0") @DecimalMax("100000") BigDecimal amount, Basis basis) {

    public enum Basis {
        PER_PERSON, TOTAL
    }

    public BudgetLimit {
        basis = basis == null ? Basis.PER_PERSON : basis;
    }

    @JsonIgnore
    public boolean isLimit() {
        return amount != null && amount.signum() > 0;
    }

    /** The limit on one traveller's share, in euros and cents; null without a limit or, for a total, a head-count. */
    public BigDecimal perPerson(Integer groupSize) {
        if (!isLimit()) {
            return null;
        }
        if (basis == Basis.PER_PERSON) {
            return amount;
        }
        return groupSize == null || groupSize <= 0 ? null
                : amount.divide(BigDecimal.valueOf(groupSize), 2, RoundingMode.DOWN);
    }
}
