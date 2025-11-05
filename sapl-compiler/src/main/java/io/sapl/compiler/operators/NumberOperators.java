package io.sapl.compiler.operators;

import java.math.RoundingMode;

import io.sapl.api.v2.ErrorValue;
import io.sapl.api.v2.NumberValue;
import io.sapl.api.v2.Value;
import lombok.experimental.UtilityClass;

/**
 * Provides arithmetic operations for NumberValue instances.
 */
@UtilityClass
public class NumberOperators {

    /**
     * Adds two number values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum of a and b, marked as secret if either operand is secret
     */
    public static NumberValue add(NumberValue a, NumberValue b) {
        return new NumberValue(a.value().add(b.value()), a.secret() || b.secret());
    }

    /**
     * Subtracts one number value from another.
     *
     * @param a the minuend
     * @param b the subtrahend
     * @return the difference of a and b, marked as secret if either operand is secret
     */
    public static NumberValue subtract(NumberValue a, NumberValue b) {
        return new NumberValue(a.value().subtract(b.value()), a.secret() || b.secret());
    }

    /**
     * Multiplies two number values.
     *
     * @param a the first factor
     * @param b the second factor
     * @return the product of a and b, marked as secret if either operand is secret
     */
    public static NumberValue multiply(NumberValue a, NumberValue b) {
        return new NumberValue(a.value().multiply(b.value()), a.secret() || b.secret());
    }

    /**
     * Divides one number value by another with HALF_UP rounding and 10 decimal places precision.
     *
     * @param a the dividend
     * @param b the divisor
     * @return the quotient of a and b, marked as secret if either operand is secret,
     *         or an ErrorValue if the divisor is zero
     */
    public static Value divide(NumberValue a, NumberValue b) {
        if (b.value().signum() == 0) {
            return new ErrorValue("Division by zero.", a.secret() || b.secret());
        }
        return new NumberValue(a.value().divide(b.value(), 10, RoundingMode.HALF_UP), a.secret() || b.secret());
    }

    /**
     * Computes the modulo of one number value by another using mathematical (Euclidean) semantics.
     * The result is always non-negative when the divisor is positive.
     *
     * @param a the dividend
     * @param b the divisor
     * @return the remainder of a divided by b, marked as secret if either operand is secret,
     *         or an ErrorValue if the divisor is zero
     */
    public static Value modulo(NumberValue a, NumberValue b) {
        if (b.value().signum() == 0) {
            return new ErrorValue("Division by zero.", a.secret() || b.secret());
        }
        var result = a.value().remainder(b.value());
        // Adjust to mathematical modulo: ensure non-negative result for positive divisor
        if (result.signum() < 0 && b.value().signum() > 0) {
            result = result.add(b.value());
        }
        return new NumberValue(result, a.secret() || b.secret());
    }
}