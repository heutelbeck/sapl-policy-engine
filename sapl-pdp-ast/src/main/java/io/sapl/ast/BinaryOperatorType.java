/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.ast;

/**
 * Binary operator ordered by precedence (lowest to highest).
 */
public enum BinaryOperatorType {
    // Logical lazy (short-circuit) - the common case
    OR,
    AND,
    // Logical eager (evaluate both sides)
    EAGER_OR,
    XOR,
    EAGER_AND,
    // Equality
    EQ,
    NE,
    REGEX,
    // Comparison
    LT,
    LE,
    GT,
    GE,
    IN,
    // Arithmetic
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    // Subtemplate
    SUBTEMPLATE;

    /** @return true for lazy (short-circuit) logical operator (OR, AND) */
    public boolean isLazy() {
        return this == OR || this == AND;
    }

    /** @return true for eager logical operator (EAGER_OR, XOR, EAGER_AND) */
    public boolean isEager() {
        return this == EAGER_OR || this == XOR || this == EAGER_AND;
    }

    /** @return true for any logical operator (lazy or eager) */
    public boolean isLogical() {
        return isLazy() || isEager();
    }

    /** @return true for arithmetic operator */
    public boolean isArithmetic() {
        return this == ADD || this == SUB || this == MUL || this == DIV || this == MOD;
    }

    /** @return true for comparison operator */
    public boolean isComparison() {
        return this == LT || this == LE || this == GT || this == GE || this == IN;
    }

    /** @return true for equality operator */
    public boolean isEquality() {
        return this == EQ || this == NE || this == REGEX;
    }
}
