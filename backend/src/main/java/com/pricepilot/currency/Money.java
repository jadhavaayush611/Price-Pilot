package com.pricepilot.currency;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary amount value object with currency qualification.
 */
@Getter
@EqualsAndHashCode
public final class Money implements Serializable, Comparable<Money> {

    private static final long serialVersionUID = 1L;

    private final BigDecimal amount;
    private final CurrencyCode currency;

    private Money(BigDecimal amount, CurrencyCode currency) {
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        if (amount == null) {
            this.amount = BigDecimal.ZERO.setScale(currency.getDecimalPrecision(), RoundingMode.HALF_UP);
        } else {
            this.amount = amount.setScale(currency.getDecimalPrecision(), RoundingMode.HALF_UP);
        }
    }

    public static Money of(BigDecimal amount, CurrencyCode currency) {
        return new Money(amount, currency);
    }

    public static Money of(double amount, CurrencyCode currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money usd(BigDecimal amount) {
        return of(amount, CurrencyCode.USD);
    }

    public static Money usd(double amount) {
        return of(amount, CurrencyCode.USD);
    }

    public static Money inr(BigDecimal amount) {
        return of(amount, CurrencyCode.INR);
    }

    public static Money inr(double amount) {
        return of(amount, CurrencyCode.INR);
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public int compareTo(Money other) {
        if (other == null) {
            return 1;
        }
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    String.format("Cannot compare Money with different currencies: %s and %s", this.currency, other.currency));
        }
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getSymbol() + amount.toPlainString() + " " + currency.name();
    }
}
