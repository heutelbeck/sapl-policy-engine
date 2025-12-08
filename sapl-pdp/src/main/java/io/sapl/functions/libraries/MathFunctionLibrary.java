/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Collection of mathematical functions for scalar operations.
 */
@UtilityClass
@FunctionLibrary(name = MathFunctionLibrary.NAME, description = MathFunctionLibrary.DESCRIPTION, libraryDocumentation = MathFunctionLibrary.DOCUMENTATION)
public class MathFunctionLibrary {

    public static final String NAME          = "math";
    public static final String DESCRIPTION   = "A collection of mathematical functions for scalar operations.";
    public static final String DOCUMENTATION = """
            # Math Function Library

            This library provides standard mathematical functions for numeric operations in policies.
            Functions include basic arithmetic operations (min, max, abs), rounding (ceil, floor, round),
            exponentiation and roots (pow, sqrt), logarithms (log, log10, logb), clamping, sign determination,
            random number generation, and mathematical constants (pi, e).

            All functions operate on JSON numbers and return numeric results or error values for invalid inputs.
            """;

    private static final String ERROR_BASE_INVALID           = "Logarithm base must be positive and not equal to 1.";
    private static final String ERROR_BOUND_MUST_BE_INTEGER  = "Bound must be an integer.";
    private static final String ERROR_BOUND_MUST_BE_POSITIVE = "Bound must be positive.";
    private static final String ERROR_LOG_REQUIRES_POSITIVE  = "Logarithm requires a positive value.";
    private static final String ERROR_MIN_GREATER_THAN_MAX   = "Minimum must be less than or equal to maximum.";
    private static final String ERROR_POWER_RESULTED_IN_NAN  = "Power operation resulted in NaN (e.g., negative base with fractional exponent).";
    private static final String ERROR_SEED_MUST_BE_INTEGER   = "Seed must be an integer.";
    private static final String ERROR_SQRT_NEGATIVE          = "Cannot calculate square root of a negative number.";

    private static final String SCHEMA_RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Returns the smaller of two numbers.
     *
     * @param a
     * first number
     * @param b
     * second number
     *
     * @return the smaller of a and b
     */
    @Function(docs = """
            ```min(NUMBER a, NUMBER b)```: Returns the smaller of two numbers.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.min(5, 3) == 3;
              math.min(-10, -5) == -10;
              math.min(2.5, 2.7) == 2.5;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value min(NumberValue a, NumberValue b) {
        return Value.of(Math.min(a.value().doubleValue(), b.value().doubleValue()));
    }

    /**
     * Returns the larger of two numbers.
     *
     * @param a
     * first number
     * @param b
     * second number
     *
     * @return the larger of a and b
     */
    @Function(docs = """
            ```max(NUMBER a, NUMBER b)```: Returns the larger of two numbers.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.max(5, 3) == 5;
              math.max(-10, -5) == -5;
              math.max(2.5, 2.7) == 2.7;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value max(NumberValue a, NumberValue b) {
        return Value.of(Math.max(a.value().doubleValue(), b.value().doubleValue()));
    }

    /**
     * Returns the absolute value of a number.
     *
     * @param value
     * the number
     *
     * @return the absolute value
     */
    @Function(docs = """
            ```abs(NUMBER value)```: Returns the absolute value of a number.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.abs(-5) == 5;
              math.abs(3.7) == 3.7;
              math.abs(0) == 0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value abs(NumberValue value) {
        return Value.of(Math.abs(value.value().doubleValue()));
    }

    /**
     * Returns the smallest integer greater than or equal to the value (rounds up).
     *
     * @param value
     * the number to round up
     *
     * @return the ceiling value
     */
    @Function(docs = """
            ```ceil(NUMBER value)```: Returns the smallest integer greater than or equal to the value (rounds up).

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.ceil(3.2) == 4.0;
              math.ceil(3.8) == 4.0;
              math.ceil(-3.2) == -3.0;
              math.ceil(5.0) == 5.0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value ceil(NumberValue value) {
        return Value.of(Math.ceil(value.value().doubleValue()));
    }

    /**
     * Returns the largest integer less than or equal to the value (rounds down).
     *
     * @param value
     * the number to round down
     *
     * @return the floor value
     */
    @Function(docs = """
            ```floor(NUMBER value)```: Returns the largest integer less than or equal to the value (rounds down).

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.floor(3.2) == 3.0;
              math.floor(3.8) == 3.0;
              math.floor(-3.2) == -4.0;
              math.floor(5.0) == 5.0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value floor(NumberValue value) {
        return Value.of(Math.floor(value.value().doubleValue()));
    }

    /**
     * Returns the value rounded to the nearest integer.
     *
     * @param value
     * the number to round
     *
     * @return the rounded value
     */
    @Function(docs = """
            ```round(NUMBER value)```: Returns the value rounded to the nearest integer. Values exactly halfway between
            two integers are rounded up (towards positive infinity).

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.round(3.2) == 3.0;
              math.round(3.8) == 4.0;
              math.round(3.5) == 4.0;
              math.round(-3.5) == -3.0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value round(NumberValue value) {
        return Value.of((double) Math.round(value.value().doubleValue()));
    }

    /**
     * Returns the value of the base raised to the power of the exponent.
     *
     * @param base
     * the base value
     * @param exponent
     * the exponent
     *
     * @return base raised to the power of exponent, or ErrorValue if result is NaN
     */
    @Function(docs = """
            ```pow(NUMBER base, NUMBER exponent)```: Returns the value of the base raised to the power of the exponent.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.pow(2, 3) == 8.0;
              math.pow(5, 2) == 25.0;
              math.pow(2, -1) == 0.5;
              math.pow(4, 0.5) == 2.0;  // square root
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value pow(NumberValue base, NumberValue exponent) {
        val result = Math.pow(base.value().doubleValue(), exponent.value().doubleValue());
        if (Double.isNaN(result)) {
            return Value.error(ERROR_POWER_RESULTED_IN_NAN);
        }
        return Value.of(result);
    }

    /**
     * Returns the square root of a number.
     *
     * @param value
     * the number
     *
     * @return the square root, or ErrorValue if value is negative
     */
    @Function(docs = """
            ```sqrt(NUMBER value)```: Returns the square root of a number. Returns an error if the value is negative.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.sqrt(16) == 4.0;
              math.sqrt(2) == 1.4142135623730951;
              math.sqrt(0) == 0.0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value sqrt(NumberValue value) {
        val number = value.value().doubleValue();
        if (number < 0) {
            return Value.error(ERROR_SQRT_NEGATIVE);
        }
        return Value.of(Math.sqrt(number));
    }

    /**
     * Returns the sign of a number.
     *
     * @param value
     * the number
     *
     * @return -1 for negative, 0 for zero, 1 for positive
     */
    @Function(docs = """
            ```sign(NUMBER value)```: Returns the sign of a number: ```-1``` for negative numbers, ```0``` for zero,
            and ```1``` for positive numbers.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.sign(-5) == -1.0;
              math.sign(0) == 0.0;
              math.sign(3.7) == 1.0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value sign(NumberValue value) {
        return Value.of(Math.signum(value.value().doubleValue()));
    }

    /**
     * Constrains a value to lie within a specified range.
     *
     * @param value
     * the value to clamp
     * @param minimum
     * the minimum bound
     * @param maximum
     * the maximum bound
     *
     * @return the clamped value, or ErrorValue if minimum greater than maximum
     */
    @Function(docs = """
            ```clamp(NUMBER value, NUMBER minimum, NUMBER maximum)```: Constrains a value to lie within a specified range.
            If the value is less than the minimum, returns the minimum. If the value is greater than the maximum, returns
            the maximum. Otherwise, returns the value unchanged.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.clamp(5, 0, 10) == 5;      // within range
              math.clamp(-5, 0, 10) == 0;     // below minimum
              math.clamp(15, 0, 10) == 10;    // above maximum
              math.clamp(10, 0, 10) == 10;    // at boundary
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value clamp(NumberValue value, NumberValue minimum, NumberValue maximum) {
        val clampValue = value.value().doubleValue();
        val minValue   = minimum.value().doubleValue();
        val maxValue   = maximum.value().doubleValue();

        if (minValue > maxValue) {
            return Value.error(ERROR_MIN_GREATER_THAN_MAX);
        }

        return Value.of(Math.clamp(clampValue, minValue, maxValue));
    }

    /**
     * Returns a cryptographically secure random integer in the range [0, bound).
     *
     * @param bound
     * the upper bound (exclusive)
     *
     * @return a random integer, or ErrorValue if bound is invalid
     */
    @Function(docs = """
            ```randomInteger(NUMBER bound)```: Returns a cryptographically secure random integer in the range ```[0, bound)```
            (inclusive of 0, exclusive of bound). Uses ```SecureRandom``` for cryptographic strength randomness.

            **Requirements:**
            - ```bound``` must be a positive integer

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var diceRoll = math.randomInteger(6) + 1;       // 1-6 inclusive
              var randomPercent = math.randomInteger(101);    // 0-100 inclusive
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value randomInteger(NumberValue bound) {
        val validation = validateIntegerBound(bound);
        if (validation != null) {
            return validation;
        }

        return Value.of(SECURE_RANDOM.nextInt(bound.value().intValue()));
    }

    /**
     * Returns a seeded random integer in the range [0, bound).
     *
     * @param bound
     * the upper bound (exclusive)
     * @param seed
     * the seed for the random number generator
     *
     * @return a random integer, or ErrorValue if bound or seed is invalid
     */
    @Function(docs = """
            ```randomIntegerSeeded(NUMBER bound, NUMBER seed)```: Returns a seeded random integer in the range ```[0, bound)```
            (inclusive of 0, exclusive of bound). The seed determines the sequence of random numbers, allowing for
            reproducible results.

            **Requirements:**
            - ```bound``` must be a positive integer
            - ```seed``` must be an integer

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.randomIntegerSeeded(10, 42) == math.randomIntegerSeeded(10, 42);  // same seed produces same result
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value randomIntegerSeeded(NumberValue bound, NumberValue seed) {
        val boundValidation = validateIntegerBound(bound);
        if (boundValidation != null) {
            return boundValidation;
        }

        val seedValidation = validateIntegerSeed(seed);
        if (seedValidation != null) {
            return seedValidation;
        }

        val random = new Random(seed.value().longValue());
        return Value.of(random.nextInt(bound.value().intValue()));
    }

    /**
     * Returns a cryptographically secure random floating-point number in the range
     * [0.0, 1.0).
     *
     * @return a random double
     */
    @Function(docs = """
            ```randomFloat()```: Returns a cryptographically secure random floating-point number in the range ```[0.0, 1.0)```
            (inclusive of 0.0, exclusive of 1.0). Uses ```SecureRandom``` for cryptographic strength randomness.

            **Technical Note:** Despite the name ```randomFloat```, this function returns a double-precision floating-point
            number (64-bit).

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var probability = math.randomFloat();           // 0.0 <= probability < 1.0
              var percentage = math.randomFloat() * 100;      // 0.0 <= percentage < 100.0
              var range = math.randomFloat() * 50 + 10;       // 10.0 <= range < 60.0
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value randomFloat() {
        return Value.of(SECURE_RANDOM.nextDouble());
    }

    /**
     * Returns a seeded random floating-point number in the range [0.0, 1.0).
     *
     * @param seed
     * the seed for the random number generator
     *
     * @return a random double, or ErrorValue if seed is invalid
     */
    @Function(docs = """
            ```randomFloatSeeded(NUMBER seed)```: Returns a seeded random floating-point number in the range ```[0.0, 1.0)```
            (inclusive of 0.0, exclusive of 1.0). The seed determines the sequence of random numbers, allowing for
            reproducible results.

            **Technical Note:** Despite the name ```randomFloatSeeded```, this function returns a double-precision floating-point
            number (64-bit).

            **Requirements:**
            - ```seed``` must be an integer

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.randomFloatSeeded(42) == math.randomFloatSeeded(42);  // same seed produces same result
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value randomFloatSeeded(NumberValue seed) {
        val seedValidation = validateIntegerSeed(seed);
        if (seedValidation != null) {
            return seedValidation;
        }

        val random = new Random(seed.value().longValue());
        return Value.of(random.nextDouble());
    }

    /**
     * Returns the mathematical constant π (pi).
     *
     * @return the value of pi
     */
    @Function(docs = """
            ```pi()```: Returns the mathematical constant π (pi), the ratio of a circle's circumference to its diameter.
            Value is approximately 3.141592653589793.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var circumference = 2 * math.pi() * radius;
              var area = math.pi() * math.pow(radius, 2);
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value pi() {
        return Value.of(Math.PI);
    }

    /**
     * Returns the mathematical constant e (Euler's number).
     *
     * @return the value of e
     */
    @Function(docs = """
            ```e()```: Returns the mathematical constant e (Euler's number), the base of natural logarithms.
            Value is approximately 2.718281828459045.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var exponentialGrowth = math.pow(math.e(), rate * time);
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value e() {
        return Value.of(Math.E);
    }

    /**
     * Returns the natural logarithm (base e) of a number.
     *
     * @param value
     * the number
     *
     * @return the natural logarithm, or ErrorValue if value is not positive
     */
    @Function(docs = """
            ```log(NUMBER value)```: Returns the natural logarithm (base e) of a number. Returns an error if the value
            is not positive.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.log(math.e()) == 1.0;
              math.log(1) == 0.0;
              math.log(10) == 2.302585092994046;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value log(NumberValue value) {
        val validation = validatePositiveValueForLogs(value);
        if (validation != null) {
            return validation;
        }

        return Value.of(Math.log(value.value().doubleValue()));
    }

    /**
     * Returns the base-10 logarithm of a number.
     *
     * @param value
     * the number
     *
     * @return the base-10 logarithm, or ErrorValue if value is not positive
     */
    @Function(docs = """
            ```log10(NUMBER value)```: Returns the base-10 logarithm of a number. Returns an error if the value is not positive.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.log10(100) == 2.0;
              math.log10(1000) == 3.0;
              math.log10(1) == 0.0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value log10(NumberValue value) {
        val validation = validatePositiveValueForLogs(value);
        if (validation != null) {
            return validation;
        }

        return Value.of(Math.log10(value.value().doubleValue()));
    }

    /**
     * Returns the logarithm of a value with an arbitrary base.
     *
     * @param value
     * the number
     * @param base
     * the logarithm base
     *
     * @return the logarithm, or ErrorValue if value or base is invalid
     */
    @Function(docs = """
            ```logb(NUMBER value, NUMBER base)```: Returns the logarithm of a value with an arbitrary base.
            Returns an error if the value is not positive or if the base is not positive and not equal to 1.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.logb(8, 2) == 3.0;     // log base 2 of 8
              math.logb(27, 3) == 3.0;    // log base 3 of 27
              math.logb(100, 10) == 2.0;  // equivalent to log10(100)
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value logb(NumberValue value, NumberValue base) {
        val valueValidation = validatePositiveValueForLogs(value);
        if (valueValidation != null) {
            return valueValidation;
        }

        val baseNumber = base.value().doubleValue();
        if (baseNumber <= 0 || baseNumber == 1) {
            return Value.error(ERROR_BASE_INVALID);
        }

        return Value.of(Math.log(value.value().doubleValue()) / Math.log(baseNumber));
    }

    private static Value validateIntegerBound(NumberValue bound) {
        val boundValue = bound.value();

        if (boundValue.scale() > 0 && boundValue.stripTrailingZeros().scale() > 0) {
            return Value.error(ERROR_BOUND_MUST_BE_INTEGER);
        }

        if (boundValue.intValue() <= 0) {
            return Value.error(ERROR_BOUND_MUST_BE_POSITIVE);
        }

        return null;
    }

    private static Value validateIntegerSeed(NumberValue seed) {
        val seedValue = seed.value();
        if (seedValue.scale() > 0 && seedValue.stripTrailingZeros().scale() > 0) {
            return Value.error(ERROR_SEED_MUST_BE_INTEGER);
        }
        return null;
    }

    private static Value validatePositiveValueForLogs(NumberValue value) {
        if (value.value().doubleValue() <= 0) {
            return Value.error(ERROR_LOG_REQUIRES_POSITIVE);
        }
        return null;
    }
}
