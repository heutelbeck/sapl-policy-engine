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
 * <p>
 * Note: SAPL is side-effect free, so eager operators ({@code &}, {@code |}) are
 * treated as
 * aliases for their lazy counterparts ({@code &&}, {@code ||}) during AST
 * transformation.
 * Only XOR ({@code ^}) remains as a distinct eager operator.
 */
public enum BinaryOperatorType {
    // Logical (all use cost-stratified short-circuit evaluation)
    OR,
    AND,
    XOR,
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

    /** @return true for short-circuit logical operator (OR, AND) */
    public boolean isLazy() {
        return this == OR || this == AND;
    }

    /** @return true for XOR (the only non-short-circuit logical operator) */
    public boolean isXor() {
        return this == XOR;
    }

    /** @return true for any logical operator */
    public boolean isLogical() {
        return this == OR || this == AND || this == XOR;
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
