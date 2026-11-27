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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.util.SaplTesting.TEST_LOCATION;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.subscriptionContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The canonical order of the operands of commutative operators follows the
 * structure of the operands, the same in every implementation of SAPL: the
 * stratum, then the value of a constant or the kind and the parts of a pure
 * operand. So of several errors among the operands the same one is the result
 * in every implementation, whatever the order written.
 */
@DisplayName("Canonical operand order")
class CanonicalOperandOrderTests {

    private static void assertOrdered(CompiledExpression earlier, CompiledExpression later) {
        assertThat(new boolean[] { CanonicalOperandOrder.precedes(earlier, later),
                CanonicalOperandOrder.precedes(later, earlier) }).containsExactly(true, false);
    }

    private static void assertEquallyRanked(CompiledExpression operand, CompiledExpression other) {
        assertThat(new boolean[] { CanonicalOperandOrder.precedes(operand, other),
                CanonicalOperandOrder.precedes(other, operand) }).containsExactly(false, false);
    }

    @Nested
    @DisplayName("Constants")
    class Constants {

        static Stream<Arguments> constantsInOrder() {
            return Stream.of(arguments("undefined before null", Value.UNDEFINED, Value.NULL),
                    arguments("null before false", Value.NULL, Value.FALSE),
                    arguments("false before true", Value.FALSE, Value.TRUE),
                    arguments("true before a number", Value.TRUE, json("-5")),
                    arguments("a smaller number before a larger one", json("-5"), json("0.5")),
                    arguments("numbers by quantity, not by digits", json("9"), json("10.0")),
                    arguments("a number before a text", json("100"), json("\"a\"")),
                    arguments("texts by bytes, capitals first", json("\"B\""), json("\"a\"")),
                    arguments("a text before its extension", json("\"a\""), json("\"ab\"")),
                    arguments("texts by code points, not by UTF-16 units", Value.of("￿"), Value.of("😀")),
                    arguments("a text before an array", json("\"z\""), json("[]")),
                    arguments("an array before its extension", json("[1]"), json("[1, 2]")),
                    arguments("arrays element by element", json("[1, 2]"), json("[2]")),
                    arguments("an array before an object", json("[5]"), json("{}")),
                    arguments("objects by their smallest key first", json("{\"b\": 1, \"a\": 2}"), json("{\"b\": 0}")),
                    arguments("objects by the value of an equal key", json("{\"a\": 1, \"b\": 9}"),
                            json("{\"a\": 2, \"b\": 0}")),
                    arguments("an object before its extension", json("{\"a\": 1}"), json("{\"a\": 1, \"b\": 1}")),
                    arguments("an object before an error", json("{\"a\": 1}"), Value.error("a")), arguments(
                            "errors by message", Value.error("Division by zero."), Value.error("Expected a number.")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("constantsInOrder")
        @DisplayName("constants are ordered by type, then by content")
        void whenConstantsDifferThenOrderedByTypeAndContent(String description, Value earlier, Value later) {
            assertOrdered(earlier, later);
        }

        static Stream<Arguments> equalConstants() {
            return Stream.of(arguments("numbers of equal quantity", json("2"), json("2.00")),
                    arguments("objects with keys in another order", json("{\"a\": 1, \"b\": 2}"),
                            json("{\"b\": 2, \"a\": 1}")),
                    arguments("errors with the same message from different places", Value.error("Same."),
                            Value.errorAt(TEST_LOCATION, "Same.")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("equalConstants")
        @DisplayName("equal constants are ranked equally")
        void whenConstantsAreEqualThenNeitherPrecedes(String description, Value operand, Value other) {
            assertEquallyRanked(operand, other);
        }
    }

    @Nested
    @DisplayName("Pure operands")
    class PureOperands {

        @ParameterizedTest(name = "{0} before {1}")
        @CsvSource(delimiter = ';', textBlock = """
                1                       ; subject
                subject                 ; subject.a
                action                  ; subject
                environment             ; resource
                action.a                ; subject.a
                subject.a               ; subject.b
                subject.a               ; subject.a.b
                subject[0]              ; subject.a
                subject[1]              ; subject[2]
                subject[-1]             ; subject[0]
                subject[:2]             ; subject[0:2]
                subject[0:2]            ; subject[0:2:2]
                subject[(action)]       ; subject[(resource)]
                subject.a               ; subject..a
                subject..*              ; subject.*
                subject[0,1]            ; subject[0,2]
                subject["a","b"]        ; subject["a","c"]
                !subject                ; -subject
                [subject]               ; -subject
                [subject]               ; [subject, 1]
                [1, subject]            ; [subject, 1]
                {"a": subject}          ; {"b": subject}
                subject + 1             ; subject + action
                subject + 2             ; subject - 1
                subject != 1            ; subject == 1
                subject * 2             ; subject * action
                subject * 2 * action    ; subject * action * resource
                subject =~ "a"          ; subject =~ "b"
                subject && action       ; subject || action
                (action && resource)    ; (subject && action)
                subject :: action       ; subject.a :: action
                """)
        @DisplayName("pure operands are ordered by stratum, kind and parts")
        void whenPureOperandsDifferThenOrderedByKindAndParts(String earlier, String later) {
            assertOrdered(compileExpression(earlier), compileExpression(later));
        }

        @ParameterizedTest(name = "{0} and {1}")
        @CsvSource(delimiter = ';', textBlock = """
                subject.a           ; subject.a
                subject && action   ; subject & action
                subject == action   ; action == subject
                {"a": subject.a}    ; {"a": subject.a}
                """)
        @DisplayName("operands of the same structure are ranked equally")
        void whenPureOperandsHaveTheSameStructureThenNeitherPrecedes(String operand, String other) {
            assertEquallyRanked(compileExpression(operand), compileExpression(other));
        }
    }

    @Nested
    @DisplayName("Errors among the operands")
    class Errors {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', textBlock = """
                (subject.n / 0) == -action.t           ; -action.t == (subject.n / 0)           ; Division by zero.
                -action.t != (subject.n / 0)           ; (subject.n / 0) != -action.t           ; Division by zero.
                (subject.n / 0) * (action.t * 1)       ; (action.t * 1) * (subject.n / 0)       ; Numeric operation requires
                (subject.n / 0) && (action.t * 1)      ; (action.t * 1) && (subject.n / 0)      ; Numeric operation requires
                (subject.t * 1) || (action.n % 0)      ; (action.n % 0) || (subject.t * 1)      ; Division by zero.
                (subject.n / 0 == 1) ^ !action.t       ; !action.t ^ (subject.n / 0 == 1)       ; Division by zero.
                """)
        @DisplayName("the error of the first operand in canonical order is the result")
        void whenSeveralPureOperandsAreErrorsThenTheFirstInCanonicalOrderIsTheResult(String written, String swapped,
                String messageStart) {
            val context       = subscriptionContext("""
                    { "subject": { "n": 1, "t": "x" }, "action": { "n": 2, "t": "y" }, "resource": null }
                    """);
            val result        = evaluateExpression(written, context);
            val swappedResult = evaluateExpression(swapped, context);
            assertThat(new Object[] { result, swappedResult })
                    .allSatisfy(value -> assertThat(value).isInstanceOfSatisfying(ErrorValue.class,
                            error -> assertThat(error.message()).startsWith(messageStart)));
        }
    }
}
