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
package io.sapl.compiler.expressions;

import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluate;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.util.SaplTesting.Evaluation;
import lombok.val;

/**
 * Kleene strong 3-valued semantics for {@code &&}/{@code ||}: the dominator
 * ({@code FALSE} for AND, {@code TRUE} for OR) wins regardless of position; an
 * error never short-circuits and is only the result when no dominator is
 * present. This must hold uniformly across constant folding, the pure tier, and
 * the stratified pure-then-stream evaluation - in particular a pure-tier error
 * must NOT skip the stream tier, because a stream may yet yield the dominator.
 */
@DisplayName("NaryBoolean Kleene semantics")
class NaryBooleanKleeneTests {

    private static final String TYPE_ERROR = "5";          // non-boolean -> error in a boolean position
    private static final Value  STRING     = Value.of("x"); // non-boolean stream/var value -> error

    @Nested
    @DisplayName("constant folding")
    class Folding {

        @Test
        @DisplayName("a constant FALSE dominates a constant error in AND, even when the error comes first")
        void whenErrorThenFalseInAndThenFalse() {
            assertThat(compileExpression(TYPE_ERROR + " && false")).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("a constant TRUE dominates a constant error in OR, even when the error comes first")
        void whenErrorThenTrueInOrThenTrue() {
            assertThat(compileExpression(TYPE_ERROR + " || true")).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("a constant error with no dominator yields the error")
        void whenErrorAndTrueThenError() {
            assertThat(compileExpression(TYPE_ERROR + " && true")).isInstanceOf(io.sapl.api.model.ErrorValue.class);
        }
    }

    @Nested
    @DisplayName("pure tier")
    class PureTier {

        @Test
        @DisplayName("a pure FALSE dominates a pure error in AND, error first")
        void whenPureErrorThenFalseThenFalse() {
            // subject is a string, so an error in boolean position, while action ==
            // "no-match" is a runtime pure FALSE.
            assertThat(evaluate("subject && (action == \"no-match\")").value()).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("a pure error with no dominator yields the error")
        void whenPureErrorAndTrueThenError() {
            assertThat(evaluate("subject && true").value()).isInstanceOf(io.sapl.api.model.ErrorValue.class);
        }
    }

    @Nested
    @DisplayName("stratified pure-then-stream (lazy && / ||)")
    class StratifiedLazy {

        @Test
        @DisplayName("a stream FALSE dominates a pure error in AND - the pure error must NOT skip the stream tier")
        void whenPureErrorAndStreamFalseThenFalse() {
            val value = evaluate("subject && <test.attr>").with("test.attr", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("a stream TRUE dominates a pure error in OR")
        void whenPureErrorOrStreamTrueThenTrue() {
            val value = evaluate("subject || <test.attr>").with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("a pure error with a stream TRUE in AND yields the error (no dominator)")
        void whenPureErrorAndStreamTrueThenError() {
            val value = evaluate("subject && <test.attr>").with("test.attr", Value.TRUE).value();
            assertThat(value).isInstanceOf(io.sapl.api.model.ErrorValue.class);
        }

        @Test
        @DisplayName("a stream error dominated by a later pure-equivalent stream FALSE in AND yields FALSE")
        void whenStreamErrorThenStreamFalseThenFalse() {
            // Lazy: the left error is not the dominator, so the right stream surfaces only
            // in a later round.
            val eval = evaluate("<test.left> && <test.right>").with("test.left", STRING).with("test.right",
                    Value.FALSE);
            assertThat(driveToStableValue(eval)).isEqualTo(Value.FALSE);
        }
    }

    private static Value driveToStableValue(Evaluation eval) {
        Value value = null;
        for (var round = 0; round < 5 && value == null; round++) {
            value = eval.step().result();
        }
        return value;
    }

    @Nested
    @DisplayName("stratified eager (& / |)")
    class StratifiedEager {

        @Test
        @DisplayName("a stream FALSE dominates a stream error in eager AND")
        void whenStreamErrorAndStreamFalseThenFalse() {
            val value = evaluate("<test.left> & <test.right>").with("test.left", STRING).with("test.right", Value.FALSE)
                    .value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("a stream error with no dominator in eager AND yields the error")
        void whenStreamErrorAndStreamTrueThenError() {
            val value = evaluate("<test.left> & <test.right>").with("test.left", STRING).with("test.right", Value.TRUE)
                    .value();
            assertThat(value).isInstanceOf(io.sapl.api.model.ErrorValue.class);
        }
    }
}
