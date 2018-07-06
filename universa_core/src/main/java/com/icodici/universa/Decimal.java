/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The decimal value used in Universa: 18 decimal points in fractional part, half-up rounding, no limits in itegral part.
 * While it is slower than plain BigDecimal, it is safer as it limits operation to the abovementioned policy, which, for
 * example, guarantees poper and same results of conversions, divisions and rounds.
 */
public class Decimal extends Number implements Comparable<Number> {

    static public final int SCALE = 18;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    static public final Decimal ZERO = new Decimal(0);
    static public final Decimal ONE = new Decimal(1);
    static public final Decimal TWO = new Decimal(2);

    private final BigDecimal value;

    public Decimal() {
        value = BigDecimal.ZERO;
    }

    public Decimal(String stringValue) {
        value = new BigDecimal(stringValue);
    }

    public Decimal(long longValue) {
        value = new BigDecimal(longValue);
    }

    public Decimal(BigDecimal bigDecimalValue) {
        value = bigDecimalValue;
    }

    @Override
    public int intValue() {
        return value.intValue();
    }

    @Override
    public long longValue() {
        return value.longValue();
    }

    @Override
    public float floatValue() {
        return value.floatValue();
    }

    @Override
    public double doubleValue() {
        return value.doubleValue();
    }

    public Decimal divide(BigDecimal divisor) {
        return new Decimal(value.divide(divisor, SCALE, ROUNDING));
    }

    public Decimal divide(Decimal divisor) {
        return new Decimal(value.divide(divisor.value, SCALE, ROUNDING));
    }

    public Decimal getIntegral() {
        return new Decimal(value.divideToIntegralValue(BigDecimal.ONE));
    }

    public Decimal getFraction() {
        return new Decimal(value.remainder(BigDecimal.ONE));
    }

    public Decimal add(Decimal augend) {
        return new Decimal(value.add(augend.value));
    }

    public Decimal subtract(Decimal subtrahend) {
        return new Decimal(value.subtract(subtrahend.value));
    }

    public Decimal multiply(Decimal muliplicand) {
        return new Decimal(value.multiply(muliplicand.value));
    }

    public Decimal remainder(Decimal divisor) {
        return new Decimal(value.remainder(divisor.value));
    }

    public Decimal add(BigDecimal augend) {
        return new Decimal(value.add(augend));
    }

    public Decimal subtract(BigDecimal subtrahend) {
        return new Decimal(value.subtract(subtrahend));
    }

    public Decimal multiply(BigDecimal muliplicand) {
        return new Decimal(value.multiply(muliplicand));
    }

    public Decimal remainder(BigDecimal divisor) {
        return new Decimal(value.remainder(divisor));
    }

    public Decimal[] divideAndRemainder(BigDecimal divisor) {
        BigDecimal[] result = value.divideAndRemainder(divisor);
        return new Decimal[]{new Decimal(result[0]), new Decimal(result[1])};
    }

    public Decimal[] divideAndRemainder(Decimal divisor) {
        BigDecimal[] result = value.divideAndRemainder(divisor.value);
        return new Decimal[]{new Decimal(result[0]), new Decimal(result[1])};
    }

    public Decimal abs() {
        return new Decimal(value.abs());
    }

    public Decimal negate() {
        return new Decimal(value.negate());
    }

    public int signum() {
        return value.signum();
    }

    public int compareTo(Number val) {
        if (val instanceof Decimal)
            return value.compareTo(((Decimal) val).value);
        if (val instanceof BigDecimal)
            return value.compareTo((BigDecimal) val);
        return value.compareTo(BigDecimal.valueOf(val.doubleValue()));
    }

    @Override
    public boolean equals(Object x) {
        if (x instanceof BigDecimal)
            return value.compareTo((BigDecimal) x) == 0;
        if (x instanceof Decimal)
            return value.compareTo(((Decimal) x).value) == 0;
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    public String toEngineeringString() {
        return value.toEngineeringString();
    }

    public String toPlainString() {
        return value.toPlainString();
    }

    public BigInteger toBigInteger() {
        return value.toBigInteger();
    }

    public Decimal ulp() {
        return new Decimal(value.ulp());
    }

}
