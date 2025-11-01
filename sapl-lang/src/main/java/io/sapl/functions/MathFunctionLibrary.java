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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Number;
import lombok.experimental.UtilityClass;

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
            # Math Function Library (name: math)

            This library provides standard mathematical functions for numeric operations in policies.
            Functions include basic arithmetic operations (min, max, abs), rounding (ceil, floor, round),
            exponentiation and roots (pow, sqrt), logarithms (log, log10, logb), clamping, sign determination,
            random number generation, and mathematical constants (pi, e).

            All functions operate on JSON numbers and return numeric results or error values for invalid inputs.
            """;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

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
            """, schema = RETURNS_NUMBER)
    public static Val min(@Number Val a, @Number Val b) {
        return Val.of(Math.min(a.get().asDouble(), b.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val max(@Number Val a, @Number Val b) {
        return Val.of(Math.max(a.get().asDouble(), b.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val abs(@Number Val value) {
        return Val.of(Math.abs(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val ceil(@Number Val value) {
        return Val.of(Math.ceil(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val floor(@Number Val value) {
        return Val.of(Math.floor(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val round(@Number Val value) {
        return Val.of((double) Math.round(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val pow(@Number Val base, @Number Val exponent) {
        final var result = Math.pow(base.get().asDouble(), exponent.get().asDouble());
        if (Double.isNaN(result)) {
            return Val.error("Power operation resulted in NaN (e.g., negative base with fractional exponent).");
        }
        return Val.of(result);
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val sqrt(@Number Val value) {
        final var number = value.get().asDouble();
        if (number < 0) {
            return Val.error("Cannot calculate square root of a negative number.");
        }
        return Val.of(Math.sqrt(number));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val sign(@Number Val value) {
        return Val.of(Math.signum(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val clamp(@Number Val value, @Number Val minimum, @Number Val maximum) {
        final var val = value.get().asDouble();
        final var min = minimum.get().asDouble();
        final var max = maximum.get().asDouble();

        if (min > max) {
            return Val.error("Minimum must be less than or equal to maximum.");
        }

        return Val.of(Math.clamp(val, min, max));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val randomInteger(@Number Val bound) {
        final var validation = validateIntegerBound(bound);
        if (validation != null) {
            return validation;
        }

        return Val.of(SECURE_RANDOM.nextInt(bound.get().asInt()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val randomIntegerSeeded(@Number Val bound, @Number Val seed) {
        final var boundValidation = validateIntegerBound(bound);
        if (boundValidation != null) {
            return boundValidation;
        }

        final var seedValidation = validateIntegerSeed(seed);
        if (seedValidation != null) {
            return seedValidation;
        }

        final var random = new Random(seed.get().asLong());
        return Val.of(random.nextInt(bound.get().asInt()));
    }

    @Function(docs = """
            ```randomFloat()```: Returns a cryptographically secure random floating-point number in the range ```[0.0, 1.0)```
            (inclusive of 0.0, exclusive of 1.0). Uses ```SecureRandom``` for cryptographic strength randomness.

            **Technical Note:** Despite the name ```randomFloat```, this function returns a double-precision floating-point
            number (64-bit) to maintain consistency with JSON number representation and Java's numeric operations.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var probability = math.randomFloat();           // 0.0 <= probability < 1.0
              var percentage = math.randomFloat() * 100;      // 0.0 <= percentage < 100.0
              var range = math.randomFloat() * 50 + 10;       // 10.0 <= range < 60.0
            ```
            """, schema = RETURNS_NUMBER)
    public static Val randomFloat() {
        return Val.of(SECURE_RANDOM.nextDouble());
    }

    @Function(docs = """
            ```randomFloatSeeded(NUMBER seed)```: Returns a seeded random floating-point number in the range ```[0.0, 1.0)```
            (inclusive of 0.0, exclusive of 1.0). The seed determines the sequence of random numbers, allowing for
            reproducible results.

            **Technical Note:** Despite the name ```randomFloatSeeded```, this function returns a double-precision floating-point
            number (64-bit) to maintain consistency with JSON number representation and Java's numeric operations.

            **Requirements:**
            - ```seed``` must be an integer

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              math.randomFloatSeeded(42) == math.randomFloatSeeded(42);  // same seed produces same result
            ```
            """, schema = RETURNS_NUMBER)
    public static Val randomFloatSeeded(@Number Val seed) {
        final var seedValidation = validateIntegerSeed(seed);
        if (seedValidation != null) {
            return seedValidation;
        }

        final var random = new Random(seed.get().asLong());
        return Val.of(random.nextDouble());
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val pi() {
        return Val.of(Math.PI);
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val e() {
        return Val.of(Math.E);
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val log(@Number Val value) {
        final var validation = validatePositiveValueForLogs(value);
        if (validation != null) {
            return validation;
        }

        return Val.of(Math.log(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val log10(@Number Val value) {
        final var validation = validatePositiveValueForLogs(value);
        if (validation != null) {
            return validation;
        }

        return Val.of(Math.log10(value.get().asDouble()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val logb(@Number Val value, @Number Val base) {
        final var valueValidation = validatePositiveValueForLogs(value);
        if (valueValidation != null) {
            return valueValidation;
        }

        final var baseNumber = base.get().asDouble();
        if (baseNumber <= 0 || baseNumber == 1) {
            return Val.error("Logarithm base must be positive and not equal to 1.");
        }

        return Val.of(Math.log(value.get().asDouble()) / Math.log(baseNumber));
    }

    /**
     * Validates that bound is a positive integer.
     */
    private static Val validateIntegerBound(Val bound) {
        final var boundNode = bound.get();

        if (!boundNode.isIntegralNumber()) {
            return Val.error("Bound must be an integer.");
        }

        if (boundNode.asInt() <= 0) {
            return Val.error("Bound must be positive.");
        }

        return null;
    }

    /**
     * Validates that seed is an integer.
     */
    private static Val validateIntegerSeed(Val seed) {
        if (!seed.get().isIntegralNumber()) {
            return Val.error("Seed must be an integer.");
        }
        return null;
    }

    /**
     * Validates that value is positive.
     */
    private static Val validatePositiveValueForLogs(Val value) {
        if (value.get().asDouble() <= 0) {
            return Val.error("Logarithm requires a positive value.");
        }
        return null;
    }
}
