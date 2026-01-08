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
package io.sapl.compiler;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for StepCompiler: all step types including KeyStep, IndexStep,
 * WildcardStep, unions, slices, conditions, and recursive descent.
 */
@DisplayName("StepCompiler")
class StepCompilerTests {

    @Nested
    @DisplayName("KeyStep")
    class KeyStepTests {

        @ParameterizedTest(name = "{0} = {1}")
        @MethodSource
        @DisplayName("on Value (compile-time)")
        void onValue(String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(arguments("{\"name\": \"alice\"}.name", Value.of("alice")),
                    arguments("{\"user\": {\"name\": \"bob\"}}.user.name", Value.of("bob")),
                    arguments("{\"a\": 1}.b", Value.UNDEFINED), arguments("{}.anyKey", Value.UNDEFINED),
                    arguments("42.foo", Value.UNDEFINED), arguments("\"hello\".length", Value.UNDEFINED),
                    arguments("null.foo", Value.UNDEFINED), arguments("true.foo", Value.UNDEFINED),
                    arguments("{\"key with space\": 123}[\"key with space\"]", Value.of(123)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("array projection")
        void arrayProjection(String description, String expr, Value expected) {
            var result = compileExpression(expr);
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> arrayProjection() {
            return Stream.of(
                    arguments("projects over all elements", "[{\"a\": 1}, {\"a\": 2}, {\"a\": 3}].a",
                            array(Value.of(1), Value.of(2), Value.of(3))),
                    arguments("skips elements without key", "[{\"a\": 1}, {\"b\": 2}, {\"a\": 3}].a",
                            array(Value.of(1), Value.of(3))),
                    arguments("skips non-objects", "[{\"a\": 1}, 42, \"str\", {\"a\": 2}].a",
                            array(Value.of(1), Value.of(2))),
                    arguments("empty array returns empty", "[].name", Value.EMPTY_ARRAY),
                    arguments("no matches returns empty", "[{\"x\": 1}, {\"y\": 2}].z", Value.EMPTY_ARRAY));
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableKey_compilesToPure() {
                assertCompilesTo("subject.name", PureOperator.class);
            }

            @Test
            void variableKey_evaluatesCorrectly() {
                var user = obj("name", Value.of("alice"), "age", Value.of(30));
                assertPureEvaluatesTo("subject.name", Map.of("subject", user), Value.of("alice"));
            }

            @Test
            void chainedKeys_workAtRuntime() {
                var user = obj("address", obj("city", Value.of("Berlin")));
                assertPureEvaluatesTo("subject.address.city", Map.of("subject", user), Value.of("Berlin"));
            }

            @Test
            void missingKey_returnsUndefined() {
                var user = obj("name", Value.of("alice"));
                assertPureEvaluatesTo("subject.missing", Map.of("subject", user), Value.UNDEFINED);
            }

            @Test
            void preserves_isDependingOnSubscription() {
                assertPureDependsOnSubscription("subject.name", true);
            }
        }
    }

    @Nested
    @DisplayName("IndexStep")
    class IndexStepTests {

        @ParameterizedTest(name = "{0} = {1}")
        @MethodSource
        @DisplayName("on Value - valid indices")
        void onValue_valid(String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        static Stream<Arguments> onValue_valid() {
            return Stream.of(arguments("[10, 20, 30][0]", Value.of(10)), arguments("[10, 20, 30][1]", Value.of(20)),
                    arguments("[10, 20, 30][-1]", Value.of(30)), arguments("[10, 20, 30][-2]", Value.of(20)));
        }

        @ParameterizedTest
        @ValueSource(strings = { "[1, 2, 3][3]", "[1, 2, 3][10]", "[1, 2, 3][-4]", "[1, 2, 3][-10]", "[][0]" })
        @DisplayName("out of bounds returns error")
        void outOfBounds_returnsError(String expr) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("out of bounds");
        }

        @ParameterizedTest
        @ValueSource(strings = { "{\"a\": 1}[0]", "42[0]", "\"hello\"[0]", "null[0]", "true[0]" })
        @DisplayName("index on non-array returns error")
        void nonArray_returnsError(String expr) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Cannot apply index step");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableIndex_compilesToPure() {
                assertCompilesTo("items[0]", PureOperator.class);
            }

            @Test
            void variableIndex_evaluatesCorrectly() {
                var items = array(Value.of("a"), Value.of("b"), Value.of("c"));
                assertPureEvaluatesTo("items[1]", Map.of("items", items), Value.of("b"));
            }

            @Test
            void negativeIndex_evaluatesCorrectly() {
                var items = array(Value.of("x"), Value.of("y"), Value.of("z"));
                assertPureEvaluatesTo("items[-1]", Map.of("items", items), Value.of("z"));
            }

            @Test
            void outOfBounds_returnsError() {
                var items = array(Value.of(1), Value.of(2));
                assertPureEvaluatesToError("items[5]", Map.of("items", items));
            }

            @Test
            void preserves_isDependingOnSubscription() {
                assertPureDependsOnSubscription("resource[0]", true);
            }
        }

        @ParameterizedTest(name = "{0} = {1}")
        @MethodSource
        @DisplayName("chaining")
        void chaining(String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        static Stream<Arguments> chaining() {
            return Stream.of(arguments("[{\"name\": \"first\"}, {\"name\": \"second\"}][1].name", Value.of("second")),
                    arguments("{\"items\": [10, 20, 30]}.items[2]", Value.of(30)),
                    arguments("[[1, 2], [3, 4], [5, 6]][1][0]", Value.of(3)),
                    arguments("{\"a\": [{\"b\": [1, 2, 3]}]}.a[0].b[-1]", Value.of(3)));
        }
    }

    @Nested
    @DisplayName("WildcardStep")
    class WildcardStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, Value expected) {
            var result = compileExpression(expr);
            if (expected instanceof ArrayValue arr && result instanceof ArrayValue resArr) {
                assertThat(resArr).containsExactlyInAnyOrderElementsOf(arr);
            } else {
                assertThat(result).isEqualTo(expected);
            }
        }

        static Stream<Arguments> onValue() {
            return Stream.of(
                    arguments("array.* returns unchanged", "[1, 2, 3].*", array(Value.of(1), Value.of(2), Value.of(3))),
                    arguments("array[*] returns unchanged", "[1, 2, 3][*]",
                            array(Value.of(1), Value.of(2), Value.of(3))),
                    arguments("empty array.* returns empty", "[].*", Value.EMPTY_ARRAY),
                    arguments("object.* returns values", "{\"a\": 1, \"b\": 2}.*", array(Value.of(1), Value.of(2))),
                    arguments("empty object.* returns empty", "{}.*", Value.EMPTY_ARRAY));
        }

        @ParameterizedTest
        @ValueSource(strings = { "42.*", "\"hello\".*", "true.*", "null.*" })
        @DisplayName("wildcard on non-collection returns error")
        void nonCollection_returnsError(String expr) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Cannot apply wildcard");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableWildcard_compilesToPure() {
                assertCompilesTo("items.*", PureOperator.class);
            }

            @Test
            void arrayVariable_evaluatesCorrectly() {
                var items = array(Value.of("x"), Value.of("y"));
                assertPureEvaluatesTo("items.*", Map.of("items", items), items);
            }

            @Test
            void objectVariable_evaluatesCorrectly() {
                var data   = obj("a", Value.of(1), "b", Value.of(2));
                var result = evaluateExpression("data.*", withVariables(Map.of("data", data)));
                assertThat(result).isInstanceOf(ArrayValue.class);
                assertThat((ArrayValue) result).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
            }
        }
    }

    @Nested
    @DisplayName("IndexUnionStep")
    class IndexUnionStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(
                    arguments("selects two indices", "[10, 20, 30, 40][0, 2]", array(Value.of(10), Value.of(30))),
                    arguments("returns in array order", "[10, 20, 30][2, 0]", array(Value.of(10), Value.of(30))),
                    arguments("negative index works", "[10, 20, 30][-1, 0]", array(Value.of(10), Value.of(30))),
                    arguments("deduplicates", "[10, 20, 30][0, 0]", array(Value.of(10))),
                    arguments("ignores out of bounds", "[10, 20, 30][100, 0]", array(Value.of(10))),
                    arguments("all out of bounds returns empty", "[10, 20, 30][100, -100]", Value.EMPTY_ARRAY),
                    arguments("negative out of bounds ignored", "[10, 20, 30][-100, 0]", array(Value.of(10))));
        }

        @ParameterizedTest
        @ValueSource(strings = { "{\"a\": 1}[0, 1]", "42[0, 1]", "\"hello\"[0, 1]", "true[0, 1]", "null[0, 1]" })
        @DisplayName("index union on non-array returns error")
        void nonArray_returnsError(String expr) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Cannot apply index union");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableIndexUnion_compilesToPure() {
                assertCompilesTo("items[0, 2]", PureOperator.class);
            }

            @Test
            void variableIndexUnion_evaluatesCorrectly() {
                var items = array(Value.of("a"), Value.of("b"), Value.of("c"), Value.of("d"));
                assertPureEvaluatesTo("items[0, 2]", Map.of("items", items), array(Value.of("a"), Value.of("c")));
            }

            @Test
            void outOfBounds_isIgnored() {
                var items = array(Value.of(1), Value.of(2));
                assertPureEvaluatesTo("items[0, 100]", Map.of("items", items), array(Value.of(1)));
            }
        }
    }

    @Nested
    @DisplayName("AttributeUnionStep")
    class AttributeUnionStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(
                    arguments("selects two attributes", "{\"a\": 1, \"b\": 2, \"c\": 3}[\"a\", \"b\"]",
                            array(Value.of(1), Value.of(2))),
                    arguments("returns in union order", "{\"a\": 1, \"b\": 2}[\"b\", \"a\"]",
                            array(Value.of(2), Value.of(1))),
                    arguments("deduplicates", "{\"a\": 1, \"b\": 2}[\"a\", \"a\"]", array(Value.of(1))),
                    arguments("ignores missing keys", "{\"a\": 1, \"b\": 2}[\"a\", \"missing\"]", array(Value.of(1))),
                    arguments("all missing returns empty", "{\"a\": 1}[\"x\", \"y\"]", Value.EMPTY_ARRAY));
        }

        @ParameterizedTest
        @ValueSource(strings = { "[1, 2, 3][\"a\", \"b\"]", "42[\"a\", \"b\"]", "\"hello\"[\"a\", \"b\"]",
                "true[\"a\", \"b\"]", "null[\"a\", \"b\"]" })
        @DisplayName("attribute union on non-object returns error")
        void nonObject_returnsError(String expr) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Cannot apply attribute union");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableAttributeUnion_compilesToPure() {
                assertCompilesTo("data[\"a\", \"b\"]", PureOperator.class);
            }

            @Test
            void variableAttributeUnion_evaluatesCorrectly() {
                var data = obj("a", Value.of(1), "b", Value.of(2), "c", Value.of(3));
                assertPureEvaluatesTo("data[\"a\", \"c\"]", Map.of("data", data), array(Value.of(1), Value.of(3)));
            }

            @Test
            void missingKeys_areIgnored() {
                var data = obj("a", Value.of(1));
                assertPureEvaluatesTo("data[\"a\", \"missing\"]", Map.of("data", data), array(Value.of(1)));
            }
        }
    }

    @Nested
    @DisplayName("SliceStep")
    class SliceStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value - valid slices")
        void onValue_valid(String description, String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        // @formatter:off
        static Stream<Arguments> onValue_valid() {
            return Stream.of(
                    // Basic slicing
                    arguments("basic [1:3]", "[10, 20, 30, 40, 50][1:3]",
                            array(Value.of(20), Value.of(30))),
                    arguments("all [:]", "[1, 2, 3][:]",
                            array(Value.of(1), Value.of(2), Value.of(3))),
                    arguments("from index [2:]", "[10, 20, 30, 40, 50][2:]",
                            array(Value.of(30), Value.of(40), Value.of(50))),
                    arguments("to index [:3]", "[10, 20, 30, 40, 50][:3]",
                            array(Value.of(10), Value.of(20), Value.of(30))),
                    // Negative indices
                    arguments("negative from [-3:]", "[10, 20, 30, 40, 50][-3:]",
                            array(Value.of(30), Value.of(40), Value.of(50))),
                    arguments("negative to [:-2]", "[10, 20, 30, 40, 50][:-2]",
                            array(Value.of(10), Value.of(20), Value.of(30))),
                    arguments("negative start positive end [-3:5]", "[10, 20, 30, 40, 50][-3:5]",
                            array(Value.of(30), Value.of(40), Value.of(50))),
                    arguments("positive start negative end [1:-2]", "[10, 20, 30, 40, 50][1:-2]",
                            array(Value.of(20), Value.of(30))),
                    arguments("both negative [-5:-2]", "[10, 20, 30, 40, 50][-5:-2]",
                            array(Value.of(10), Value.of(20), Value.of(30))),
                    // Steps
                    arguments("step 2 [::2]", "[10, 20, 30, 40, 50][::2]",
                            array(Value.of(10), Value.of(30), Value.of(50))),
                    arguments("from to step [1:5:2]", "[10, 20, 30, 40, 50][1:5:2]",
                            array(Value.of(20), Value.of(40))),
                    arguments("reverse [::-1]", "[1, 2, 3][::-1]",
                            array(Value.of(3), Value.of(2), Value.of(1))),
                    arguments("reverse explicit bounds [4:1:-1]", "[10, 20, 30, 40, 50][4:1:-1]",
                            array(Value.of(50), Value.of(40), Value.of(30))),
                    arguments("reverse from index [3::-1]", "[10, 20, 30, 40, 50][3::-1]",
                            array(Value.of(40), Value.of(30), Value.of(20), Value.of(10))),
                    arguments("reverse from last [-1::-1]", "[1, 2, 3][-1::-1]",
                            array(Value.of(3), Value.of(2), Value.of(1))),
                    // Edge cases
                    arguments("from >= to returns empty [3:1]", "[1, 2, 3, 4, 5][3:1]", Value.EMPTY_ARRAY),
                    arguments("start equals end [1:1]", "[1, 2, 3, 4, 5][1:1]", Value.EMPTY_ARRAY),
                    arguments("empty array slice", "[][1:3]", Value.EMPTY_ARRAY),
                    // Out of bounds handling
                    arguments("start beyond length [10:]", "[1, 2, 3][10:]", Value.EMPTY_ARRAY),
                    arguments("end beyond length [:10]", "[1, 2, 3][:10]",
                            array(Value.of(1), Value.of(2), Value.of(3))),
                    arguments("negative start beyond length [-10:]", "[1, 2, 3][-10:]",
                            array(Value.of(1), Value.of(2), Value.of(3))));
        }
        // @formatter:on

        @Test
        @DisplayName("step zero returns error")
        void stepZero_returnsError() {
            var result = compileExpression("[1, 2, 3][::0]");
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("zero");
        }

        @ParameterizedTest
        @ValueSource(strings = { "{\"a\": 1}[1:3]", "42[1:3]", "\"hello\"[1:3]", "true[1:3]", "null[1:3]" })
        @DisplayName("slice on non-array returns error")
        void nonArray_returnsError(String expr) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Cannot apply slice");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableSlice_compilesToPure() {
                assertCompilesTo("items[1:3]", PureOperator.class);
            }

            @Test
            void variableSlice_evaluatesCorrectly() {
                var items = array(Value.of(10), Value.of(20), Value.of(30), Value.of(40));
                assertPureEvaluatesTo("items[1:3]", Map.of("items", items), array(Value.of(20), Value.of(30)));
            }
        }
    }

    @Nested
    @DisplayName("ExpressionStep")
    class ExpressionStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, Value expected) {
            assertCompilesTo(expr, expected);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(arguments("dynamic index [(0)]", "[10, 20, 30][(0)]", Value.of(10)),
                    arguments("calculated index [(1+1)]", "[10, 20, 30][(1+1)]", Value.of(30)),
                    arguments("negative dynamic index [(-1)]", "[10, 20, 30][(-1)]", Value.of(30)),
                    arguments("dynamic key [(\"a\")]", "{\"a\": 1, \"b\": 2}[(\"a\")]", Value.of(1)),
                    arguments("calculated key [(\"a\" + \"b\")]", "{\"ab\": 42}[(\"a\" + \"b\")]", Value.of(42)),
                    arguments("missing key returns undefined", "{\"a\": 1}[(\"missing\")]", Value.UNDEFINED),
                    arguments("array projection with dynamic key", "[{\"x\": 1}, {\"x\": 2}][(\"x\")]",
                            array(Value.of(1), Value.of(2))));
        }

        @Test
        @DisplayName("invalid type returns error")
        void invalidType_returnsError() {
            var result = compileExpression("[1, 2, 3][(true)]");
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("number or string");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableExpressionStep_compilesToPure() {
                assertCompilesTo("items[(0)]", PureOperator.class);
            }

            @Test
            void variableExpressionStep_evaluatesCorrectly() {
                var items = array(Value.of("a"), Value.of("b"), Value.of("c"));
                assertPureEvaluatesTo("items[(1)]", Map.of("items", items), Value.of("b"));
            }
        }
    }

    @Nested
    @DisplayName("ConditionStep")
    class ConditionStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value - constant folds")
        void onValue(String description, String expr, Value expected) {
            var result = compileExpression(expr);
            if (expected instanceof ArrayValue arr && result instanceof ArrayValue resArr) {
                assertThat(resArr).containsExactlyInAnyOrderElementsOf(arr);
            } else {
                assertThat(result).isEqualTo(expected);
            }
        }

        static Stream<Arguments> onValue() {
            return Stream.of(
                    arguments("greater than [?(@ > 3)]", "[1, 2, 3, 4, 5][?(@ > 3)]", array(Value.of(4), Value.of(5))),
                    arguments("less than [?(@ < 3)]", "[1, 2, 3, 4, 5][?(@ < 3)]", array(Value.of(1), Value.of(2))),
                    arguments("equals [?(@ == 2)]", "[1, 2, 3, 2, 1][?(@ == 2)]", array(Value.of(2), Value.of(2))),
                    arguments("by index [?(# > 2)]", "[10, 20, 30, 40, 50][?(# > 2)]",
                            array(Value.of(40), Value.of(50))),
                    arguments("all true [?(true)]", "[1, 2, 3][?(true)]", array(Value.of(1), Value.of(2), Value.of(3))),
                    arguments("all false [?(false)]", "[1, 2, 3][?(false)]", Value.EMPTY_ARRAY),
                    arguments("empty array", "[][?(@ > 0)]", Value.EMPTY_ARRAY),
                    arguments("object filtering", "{\"a\": 1, \"b\": 2, \"c\": 3}[?(@ > 1)]",
                            array(Value.of(2), Value.of(3))),
                    arguments("compound condition", "[1, 2, 3, 4, 5][?(@ > 1 && @ < 5)]",
                            array(Value.of(2), Value.of(3), Value.of(4))));
        }

        @Test
        @DisplayName("condition on scalar returns error")
        void onScalar_returnsError() {
            var result = compileExpression("42[?(@ > 0)]");
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Cannot apply condition");
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableCondition_compilesToPure() {
                assertCompilesTo("items[?(@ > 0)]", PureOperator.class);
            }

            @Test
            void variableCondition_evaluatesCorrectly() {
                var items = array(Value.of(1), Value.of(2), Value.of(3), Value.of(4));
                assertPureEvaluatesTo("items[?(@ > 2)]", Map.of("items", items), array(Value.of(3), Value.of(4)));
            }
        }
    }

    @Nested
    @DisplayName("RecursiveKeyStep")
    class RecursiveKeyStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, int expectedSize) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ArrayValue.class);
            assertThat((ArrayValue) result).hasSize(expectedSize);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(arguments("simple object", "{\"a\": 1, \"b\": 2}..a", 1),
                    arguments("nested objects", "{\"a\": {\"a\": 1}}..a", 2),
                    arguments("array of objects", "[{\"x\": 1}, {\"x\": 2}]..x", 2),
                    arguments("deep nesting", "{\"x\": {\"y\": {\"z\": 42}}}..z", 1));
        }

        @Test
        @DisplayName("key not found returns empty")
        void keyNotFound_returnsEmpty() {
            assertCompilesTo("{\"a\": 1}..b", Value.EMPTY_ARRAY);
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableRecursiveKey_compilesToPure() {
                assertCompilesTo("data..name", PureOperator.class);
            }

            @Test
            void variableRecursiveKey_evaluatesCorrectly() {
                var data = obj("items", array(obj("name", Value.of("a")), obj("name", Value.of("b"))));
                assertPureEvaluatesTo("data..name", Map.of("data", data), array(Value.of("a"), Value.of("b")));
            }
        }
    }

    @Nested
    @DisplayName("RecursiveIndexStep")
    class RecursiveIndexStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, int expectedSize) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ArrayValue.class);
            assertThat((ArrayValue) result).hasSize(expectedSize);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(arguments("finds first element", "[10, 20, 30]..[0]", 1),
                    arguments("nested arrays", "[[1, 2], [3, 4]]..[0]", 3),
                    arguments("object with arrays", "{\"a\": [1, 2], \"b\": [3, 4]}..[0]", 2));
        }

        @Test
        @DisplayName("negative index works")
        void negativeIndex() {
            assertCompilesTo("[10, 20, 30]..[-1]", array(Value.of(30)));
        }

        @Test
        @DisplayName("empty array returns empty")
        void emptyArray() {
            assertCompilesTo("[]..[0]", Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("out of bounds returns empty")
        void outOfBounds() {
            assertCompilesTo("[1, 2]..[5]", Value.EMPTY_ARRAY);
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableRecursiveIndex_compilesToPure() {
                assertCompilesTo("data..[0]", PureOperator.class);
            }

            @Test
            void variableRecursiveIndex_evaluatesCorrectly() {
                var data = array(Value.of(10), Value.of(20));
                assertPureEvaluatesTo("data..[0]", Map.of("data", data), array(Value.of(10)));
            }
        }
    }

    @Nested
    @DisplayName("RecursiveWildcardStep")
    class RecursiveWildcardStepTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("on Value")
        void onValue(String description, String expr, int expectedSize) {
            var result = compileExpression(expr);
            assertThat(result).isInstanceOf(ArrayValue.class);
            assertThat((ArrayValue) result).hasSize(expectedSize);
        }

        static Stream<Arguments> onValue() {
            return Stream.of(arguments("flat array", "[1, 2, 3]..*", 3),
                    arguments("nested structure", "{\"a\": 1, \"b\": {\"c\": 2}}..*", 3));
        }

        @Test
        @DisplayName("empty object returns empty")
        void emptyObject() {
            assertCompilesTo("{}..*", Value.EMPTY_ARRAY);
        }

        @Test
        @DisplayName("empty array returns empty")
        void emptyArray() {
            assertCompilesTo("[]..*", Value.EMPTY_ARRAY);
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            void variableRecursiveWildcard_compilesToPure() {
                assertCompilesTo("data..*", PureOperator.class);
            }

            @Test
            void variableRecursiveWildcard_evaluatesCorrectly() {
                var data = array(Value.of(1), Value.of(2));
                assertPureEvaluatesTo("data..*", Map.of("data", data), array(Value.of(1), Value.of(2)));
            }
        }
    }

    @Nested
    @DisplayName("Error Propagation")
    class ErrorPropagationTests {

        private static final Value ERROR = Value.error("test error");

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("step on error propagates error")
        void stepOnError_propagatesError(String description, Value result) {
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                    .isEqualTo("test error");
        }

        static Stream<Arguments> stepOnError_propagatesError() {
            return Stream.of(arguments("key step", StepCompiler.applyKeyStep(ERROR, "key")),
                    arguments("index step", StepCompiler.applyIndexStep(ERROR, 0, null)),
                    arguments("wildcard step", StepCompiler.applyWildcardStep(ERROR, null)),
                    arguments("index union step", StepCompiler.applyIndexUnionStep(ERROR, List.of(0, 1), null)),
                    arguments("attribute union step",
                            StepCompiler.applyAttributeUnionStep(ERROR, List.of("a", "b"), null)));
        }
    }
}
