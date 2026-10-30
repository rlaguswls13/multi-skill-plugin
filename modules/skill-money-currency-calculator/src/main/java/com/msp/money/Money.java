package com.msp.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 부동소수점 오차 없는 통화(Currency) 금액 불변 객체 및 금융 연산기.
 * 기본 반올림 모드는 은행가 반올림(HALF_EVEN)을 사용합니다.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(currency.defaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money of(long amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(double amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        checkSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money minus(Money other) {
        checkSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(double factor) {
        BigDecimal res = this.amount.multiply(BigDecimal.valueOf(factor));
        return new Money(res, this.currency);
    }

    public Money divide(BigDecimal divisor, RoundingMode mode) {
        Objects.requireNonNull(divisor, "divisor must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        BigDecimal res = this.amount.divide(divisor, this.currency.defaultFractionDigits(), mode);
        return new Money(res, this.currency);
    }

    public Money convert(Currency targetCurrency, BigDecimal exchangeRate) {
        Objects.requireNonNull(targetCurrency, "targetCurrency must not be null");
        Objects.requireNonNull(exchangeRate, "exchangeRate must not be null");
        BigDecimal converted = this.amount.multiply(exchangeRate);
        return new Money(converted, targetCurrency);
    }

    public String format() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(currency.defaultFractionDigits());
        nf.setMaximumFractionDigits(currency.defaultFractionDigits());
        return currency.symbol() + nf.format(this.amount);
    }

    private void checkSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money must not be null");
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(String.format("Currency mismatch: %s vs %s", this.currency, other.currency));
        }
    }

    @Override
    public int compareTo(Money o) {
        checkSameCurrency(o);
        return this.amount.compareTo(o.amount);
    }

    @Override
    public String toString() {
        return format() + " " + currency.name();
    }
}
