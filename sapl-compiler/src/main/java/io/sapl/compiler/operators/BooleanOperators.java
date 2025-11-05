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
package io.sapl.compiler.operators;

import io.sapl.api.v2.BooleanValue;
import io.sapl.api.v2.Value;
import lombok.experimental.UtilityClass;

/**
 * Provides logical operations for BooleanValue instances.
 */
@UtilityClass
public class BooleanOperators {

    /**
     * Performs logical AND operation on two boolean values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the logical AND of a and b, marked as secret if either operand is secret
     */
    public static BooleanValue and(BooleanValue a, BooleanValue b) {
        return preserveSecret(a.value() && b.value(), a.secret() || b.secret());
    }

    /**
     * Performs logical OR operation on two boolean values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the logical OR of a and b, marked as secret if either operand is secret
     */
    public static BooleanValue or(BooleanValue a, BooleanValue b) {
        return preserveSecret(a.value() || b.value(), a.secret() || b.secret());
    }

    /**
     * Performs logical XOR operation on two boolean values.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the logical XOR of a and b, marked as secret if either operand is secret
     */
    public static BooleanValue xor(BooleanValue a, BooleanValue b) {
        return preserveSecret(a.value() ^ b.value(), a.secret() || b.secret());
    }

    /**
     * Performs logical NOT operation on a boolean value.
     *
     * @param a the operand to negate
     * @return the logical NOT of a, preserving its secret status
     */
    public static BooleanValue not(BooleanValue a) {
        return preserveSecret(!a.value(), a.secret());
    }

    /**
     * Creates a BooleanValue with appropriate secret handling, reusing constants when possible.
     *
     * @param value the boolean value
     * @param secret whether the value should be marked as secret
     * @return a BooleanValue with the specified value and secret flag
     */
    private static BooleanValue preserveSecret(boolean value, boolean secret) {
        if (secret) {
            return value ? BooleanValue.SECRET_TRUE : BooleanValue.SECRET_FALSE;
        } else {
            return value ? Value.TRUE : Value.FALSE;
        }
    }
}