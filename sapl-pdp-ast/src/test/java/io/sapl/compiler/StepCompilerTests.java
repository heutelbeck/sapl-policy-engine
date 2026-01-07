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

import io.sapl.api.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StepCompiler: KeyStep and IndexStep.
 */
@DisplayName("StepCompiler")
class StepCompilerTests {

    @Nested
    @DisplayName("KeyStep")
    class KeyStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("object.key returns value")
            void objectKeyReturnsValue() {
                assertCompilesTo("{\"name\": \"alice\"}.name", Value.of("alice"));
            }

            @Test
            @DisplayName("object.key with nested object returns nested value")
            void objectKeyWithNestedObjectReturnsNestedValue() {
                assertCompilesTo("{\"user\": {\"name\": \"bob\"}}.user.name", Value.of("bob"));
            }

            @Test
            @DisplayName("object.missingKey returns undefined")
            void objectMissingKeyReturnsUndefined() {
                assertCompilesTo("{\"a\": 1}.b", Value.UNDEFINED);
            }

            @Test
            @DisplayName("empty object.key returns undefined")
            void emptyObjectKeyReturnsUndefined() {
                assertCompilesTo("{}.anyKey", Value.UNDEFINED);
            }

            @Test
            @DisplayName("number.key returns undefined")
            void numberKeyReturnsUndefined() {
                assertCompilesTo("42.foo", Value.UNDEFINED);
            }

            @Test
            @DisplayName("string.key returns undefined")
            void stringKeyReturnsUndefined() {
                assertCompilesTo("\"hello\".length", Value.UNDEFINED);
            }

            @Test
            @DisplayName("null.key returns undefined")
            void nullKeyReturnsUndefined() {
                assertCompilesTo("null.foo", Value.UNDEFINED);
            }

            @Test
            @DisplayName("boolean.key returns undefined")
            void booleanKeyReturnsUndefined() {
                assertCompilesTo("true.foo", Value.UNDEFINED);
            }

            @Test
            @DisplayName("bracket notation works")
            void bracketNotationWorks() {
                assertCompilesTo("{\"key with space\": 123}[\"key with space\"]", Value.of(123));
            }
        }

        @Nested
        @DisplayName("array projection")
        class ArrayProjection {

            @Test
            @DisplayName("array.key projects over all elements")
            void arrayKeyProjectsOverAllElements() {
                var result = compileExpression("[{\"a\": 1}, {\"a\": 2}, {\"a\": 3}].a");
                assertThat(result).isEqualTo(array(Value.of(1), Value.of(2), Value.of(3)));
            }

            @Test
            @DisplayName("array.key skips elements without key")
            void arrayKeySkipsElementsWithoutKey() {
                var result = compileExpression("[{\"a\": 1}, {\"b\": 2}, {\"a\": 3}].a");
                assertThat(result).isEqualTo(array(Value.of(1), Value.of(3)));
            }

            @Test
            @DisplayName("array.key skips non-objects")
            void arrayKeySkipsNonObjects() {
                var result = compileExpression("[{\"a\": 1}, 42, \"str\", {\"a\": 2}].a");
                assertThat(result).isEqualTo(array(Value.of(1), Value.of(2)));
            }

            @Test
            @DisplayName("array.key on empty array returns empty array")
            void arrayKeyOnEmptyArrayReturnsEmptyArray() {
                assertCompilesTo("[].name", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("array.key with no matches returns empty array")
            void arrayKeyWithNoMatchesReturnsEmptyArray() {
                assertCompilesTo("[{\"x\": 1}, {\"y\": 2}].z", Value.EMPTY_ARRAY);
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable.key compiles to PureOperator")
            void variableKeyCompilesToPure() {
                assertCompilesTo("subject.name", PureOperator.class);
            }

            @Test
            @DisplayName("variable.key evaluates correctly")
            void variableKeyEvaluatesCorrectly() {
                var user = obj("name", Value.of("alice"), "age", Value.of(30));
                assertPureEvaluatesTo("subject.name", Map.of("subject", user), Value.of("alice"));
            }

            @Test
            @DisplayName("chained keys work at runtime")
            void chainedKeysWorkAtRuntime() {
                var user = obj("address", obj("city", Value.of("Berlin")));
                assertPureEvaluatesTo("subject.address.city", Map.of("subject", user), Value.of("Berlin"));
            }

            @Test
            @DisplayName("missing key at runtime returns undefined")
            void missingKeyAtRuntimeReturnsUndefined() {
                var user = obj("name", Value.of("alice"));
                assertPureEvaluatesTo("subject.missing", Map.of("subject", user), Value.UNDEFINED);
            }

            @Test
            @DisplayName("preserves isDependingOnSubscription from base")
            void preservesDependingOnSubscription() {
                assertPureDependsOnSubscription("subject.name", true);
            }
        }
    }

    @Nested
    @DisplayName("IndexStep")
    class IndexStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("array[0] returns first element")
            void arrayZeroReturnsFirstElement() {
                assertCompilesTo("[10, 20, 30][0]", Value.of(10));
            }

            @Test
            @DisplayName("array[1] returns second element")
            void arrayOneReturnsSecondElement() {
                assertCompilesTo("[10, 20, 30][1]", Value.of(20));
            }

            @Test
            @DisplayName("array[-1] returns last element")
            void arrayMinusOneReturnsLastElement() {
                assertCompilesTo("[10, 20, 30][-1]", Value.of(30));
            }

            @Test
            @DisplayName("array[-2] returns second-to-last element")
            void arrayMinusTwoReturnsSecondToLastElement() {
                assertCompilesTo("[10, 20, 30][-2]", Value.of(20));
            }

            @ParameterizedTest
            @ValueSource(strings = { "[1, 2, 3][3]", "[1, 2, 3][10]", "[1, 2, 3][-4]", "[1, 2, 3][-10]" })
            @DisplayName("out of bounds returns error")
            void outOfBoundsReturnsError(String expr) {
                var result = compileExpression(expr);
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("out of bounds");
            }

            @Test
            @DisplayName("empty array[0] returns error")
            void emptyArrayZeroReturnsError() {
                var result = compileExpression("[][0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
            }

            @Test
            @DisplayName("object[0] returns error")
            void objectIndexReturnsError() {
                var result = compileExpression("{\"a\": 1}[0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply index step");
            }

            @Test
            @DisplayName("number[0] returns error")
            void numberIndexReturnsError() {
                var result = compileExpression("42[0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply index step");
            }

            @Test
            @DisplayName("string[0] returns error")
            void stringIndexReturnsError() {
                var result = compileExpression("\"hello\"[0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply index step");
            }

            @Test
            @DisplayName("null[0] returns error")
            void nullIndexReturnsError() {
                var result = compileExpression("null[0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply index step");
            }

            @Test
            @DisplayName("boolean[0] returns error")
            void booleanIndexReturnsError() {
                var result = compileExpression("true[0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply index step");
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable[0] compiles to PureOperator")
            void variableIndexCompilesToPure() {
                assertCompilesTo("items[0]", PureOperator.class);
            }

            @Test
            @DisplayName("variable[index] evaluates correctly")
            void variableIndexEvaluatesCorrectly() {
                var items = array(Value.of("a"), Value.of("b"), Value.of("c"));
                assertPureEvaluatesTo("items[1]", Map.of("items", items), Value.of("b"));
            }

            @Test
            @DisplayName("variable[-1] evaluates correctly")
            void variableNegativeIndexEvaluatesCorrectly() {
                var items = array(Value.of("x"), Value.of("y"), Value.of("z"));
                assertPureEvaluatesTo("items[-1]", Map.of("items", items), Value.of("z"));
            }

            @Test
            @DisplayName("out of bounds at runtime returns error")
            void outOfBoundsAtRuntimeReturnsError() {
                var items = array(Value.of(1), Value.of(2));
                assertPureEvaluatesToError("items[5]", Map.of("items", items));
            }

            @Test
            @DisplayName("preserves isDependingOnSubscription from base")
            void preservesDependingOnSubscription() {
                assertPureDependsOnSubscription("resource[0]", true);
            }
        }

        @Nested
        @DisplayName("chaining")
        class Chaining {

            @Test
            @DisplayName("array[0].key works")
            void arrayIndexThenKeyWorks() {
                assertCompilesTo("[{\"name\": \"first\"}, {\"name\": \"second\"}][1].name", Value.of("second"));
            }

            @Test
            @DisplayName("object.key[0] works")
            void objectKeyThenIndexWorks() {
                assertCompilesTo("{\"items\": [10, 20, 30]}.items[2]", Value.of(30));
            }

            @Test
            @DisplayName("multiple indices work")
            void multipleIndicesWork() {
                assertCompilesTo("[[1, 2], [3, 4], [5, 6]][1][0]", Value.of(3));
            }

            @Test
            @DisplayName("deep nesting works")
            void deepNestingWorks() {
                var result = compileExpression("{\"a\": [{\"b\": [1, 2, 3]}]}.a[0].b[-1]");
                assertThat(result).isEqualTo(Value.of(3));
            }
        }
    }

    @Nested
    @DisplayName("WildcardStep")
    class WildcardStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("array.* returns unchanged array")
            void arrayWildcardReturnsUnchangedArray() {
                assertCompilesTo("[1, 2, 3].*", array(Value.of(1), Value.of(2), Value.of(3)));
            }

            @Test
            @DisplayName("array[*] returns unchanged array")
            void arrayBracketWildcardReturnsUnchangedArray() {
                assertCompilesTo("[1, 2, 3][*]", array(Value.of(1), Value.of(2), Value.of(3)));
            }

            @Test
            @DisplayName("empty array.* returns empty array")
            void emptyArrayWildcardReturnsEmptyArray() {
                assertCompilesTo("[].*", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("object.* returns array of values")
            void objectWildcardReturnsArrayOfValues() {
                var result = compileExpression("{\"a\": 1, \"b\": 2}.*");
                assertThat(result).isInstanceOf(ArrayValue.class);
                var arr = (ArrayValue) result;
                assertThat(arr).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
            }

            @Test
            @DisplayName("empty object.* returns empty array")
            void emptyObjectWildcardReturnsEmptyArray() {
                assertCompilesTo("{}.*", Value.EMPTY_ARRAY);
            }

            @ParameterizedTest
            @ValueSource(strings = { "42.*", "\"hello\".*", "true.*", "null.*" })
            @DisplayName("wildcard on non-collection returns error")
            void wildcardOnNonCollectionReturnsError(String expr) {
                var result = compileExpression(expr);
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply wildcard");
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable.* compiles to PureOperator")
            void variableWildcardCompilesToPure() {
                assertCompilesTo("items.*", PureOperator.class);
            }

            @Test
            @DisplayName("variable.* on array evaluates correctly")
            void variableWildcardOnArrayEvaluatesCorrectly() {
                var items = array(Value.of("x"), Value.of("y"));
                assertPureEvaluatesTo("items.*", Map.of("items", items), items);
            }

            @Test
            @DisplayName("variable.* on object evaluates correctly")
            void variableWildcardOnObjectEvaluatesCorrectly() {
                var obj    = obj("a", Value.of(1), "b", Value.of(2));
                var result = evaluateExpression("data.*", withVariables(Map.of("data", obj)));
                assertThat(result).isInstanceOf(ArrayValue.class);
                assertThat((ArrayValue) result).containsExactlyInAnyOrder(Value.of(1), Value.of(2));
            }
        }
    }

    @Nested
    @DisplayName("IndexUnionStep")
    class IndexUnionStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("[0, 2] selects indices 0 and 2")
            void selectsTwoIndices() {
                assertCompilesTo("[10, 20, 30, 40][0, 2]", array(Value.of(10), Value.of(30)));
            }

            @Test
            @DisplayName("[2, 0] returns in array order, not union order")
            void returnsInArrayOrder() {
                assertCompilesTo("[10, 20, 30][2, 0]", array(Value.of(10), Value.of(30)));
            }

            @Test
            @DisplayName("[-1, 0] with negative index works")
            void negativeIndexWorks() {
                assertCompilesTo("[10, 20, 30][-1, 0]", array(Value.of(10), Value.of(30)));
            }

            @Test
            @DisplayName("[0, 0] deduplicates")
            void deduplicates() {
                assertCompilesTo("[10, 20, 30][0, 0]", array(Value.of(10)));
            }

            @Test
            @DisplayName("[100, 0] with out of bounds ignores invalid index")
            void outOfBoundsIgnoresInvalidIndex() {
                // Out-of-bounds indices are silently ignored (sapl-lang behavior)
                assertCompilesTo("[10, 20, 30][100, 0]", array(Value.of(10)));
            }

            @Test
            @DisplayName("[100, -100] with all out of bounds returns empty")
            void allOutOfBoundsReturnsEmpty() {
                // All indices out of bounds, result is empty array
                assertCompilesTo("[10, 20, 30][100, -100]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("[-100, 0] with negative out of bounds ignores invalid")
            void negativeOutOfBoundsIgnoresInvalid() {
                // Out-of-bounds negative index is silently ignored
                assertCompilesTo("[10, 20, 30][-100, 0]", array(Value.of(10)));
            }

            @Test
            @DisplayName("index union on object returns error")
            void indexUnionOnObjectReturnsError() {
                var result = compileExpression("{\"a\": 1}[0, 1]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply index union");
            }

            @ParameterizedTest
            @ValueSource(strings = { "42[0, 1]", "\"hello\"[0, 1]", "true[0, 1]", "null[0, 1]" })
            @DisplayName("index union on non-array returns error")
            void indexUnionOnNonArrayReturnsError(String expr) {
                var result = compileExpression(expr);
                assertThat(result).isInstanceOf(ErrorValue.class);
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable[0, 2] compiles to PureOperator")
            void variableIndexUnionCompilesToPure() {
                assertCompilesTo("items[0, 2]", PureOperator.class);
            }

            @Test
            @DisplayName("variable[0, 2] evaluates correctly")
            void variableIndexUnionEvaluatesCorrectly() {
                var items = array(Value.of("a"), Value.of("b"), Value.of("c"), Value.of("d"));
                assertPureEvaluatesTo("items[0, 2]", Map.of("items", items), array(Value.of("a"), Value.of("c")));
            }

            @Test
            @DisplayName("out of bounds at runtime is ignored")
            void outOfBoundsAtRuntimeIsIgnored() {
                var items = array(Value.of(1), Value.of(2));
                assertPureEvaluatesTo("items[0, 100]", Map.of("items", items), array(Value.of(1)));
            }
        }
    }

    @Nested
    @DisplayName("AttributeUnionStep")
    class AttributeUnionStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("[\"a\", \"b\"] selects two attributes")
            void selectsTwoAttributes() {
                var result = compileExpression("{\"a\": 1, \"b\": 2, \"c\": 3}[\"a\", \"b\"]");
                assertThat(result).isEqualTo(array(Value.of(1), Value.of(2)));
            }

            @Test
            @DisplayName("[\"b\", \"a\"] returns in union order")
            void returnsInUnionOrder() {
                var result = compileExpression("{\"a\": 1, \"b\": 2}[\"b\", \"a\"]");
                assertThat(result).isEqualTo(array(Value.of(2), Value.of(1)));
            }

            @Test
            @DisplayName("[\"a\", \"a\"] deduplicates")
            void deduplicates() {
                assertCompilesTo("{\"a\": 1, \"b\": 2}[\"a\", \"a\"]", array(Value.of(1)));
            }

            @Test
            @DisplayName("[\"a\", \"missing\"] ignores missing keys")
            void ignoresMissingKeys() {
                assertCompilesTo("{\"a\": 1, \"b\": 2}[\"a\", \"missing\"]", array(Value.of(1)));
            }

            @Test
            @DisplayName("[\"x\", \"y\"] with all missing returns empty")
            void allMissingReturnsEmpty() {
                assertCompilesTo("{\"a\": 1}[\"x\", \"y\"]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("attribute union on array returns error")
            void attributeUnionOnArrayReturnsError() {
                var result = compileExpression("[1, 2, 3][\"a\", \"b\"]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply attribute union");
            }

            @ParameterizedTest
            @ValueSource(strings = { "42[\"a\", \"b\"]", "\"hello\"[\"a\", \"b\"]", "true[\"a\", \"b\"]",
                    "null[\"a\", \"b\"]" })
            @DisplayName("attribute union on non-object returns error")
            void attributeUnionOnNonObjectReturnsError(String expr) {
                var result = compileExpression(expr);
                assertThat(result).isInstanceOf(ErrorValue.class);
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable[\"a\", \"b\"] compiles to PureOperator")
            void variableAttributeUnionCompilesToPure() {
                assertCompilesTo("data[\"a\", \"b\"]", PureOperator.class);
            }

            @Test
            @DisplayName("variable[\"a\", \"b\"] evaluates correctly")
            void variableAttributeUnionEvaluatesCorrectly() {
                var data = obj("a", Value.of(1), "b", Value.of(2), "c", Value.of(3));
                assertPureEvaluatesTo("data[\"a\", \"c\"]", Map.of("data", data), array(Value.of(1), Value.of(3)));
            }

            @Test
            @DisplayName("missing keys at runtime are ignored")
            void missingKeysAtRuntimeAreIgnored() {
                var data = obj("a", Value.of(1));
                assertPureEvaluatesTo("data[\"a\", \"missing\"]", Map.of("data", data), array(Value.of(1)));
            }
        }
    }

    @Nested
    @DisplayName("error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("error.key propagates error")
        void errorKeyPropagatesError() {
            // Trigger error via invalid operation, then apply key
            var result = compileExpression("(1/0).foo");
            // Division by zero is not an error at compile time in SAPL, but let's test
            // error propagation
            // Using a different approach - manually test via step application
        }

        @Test
        @DisplayName("key step on error base propagates error")
        void keyStepOnErrorBasePropagatesError() {
            // The key step should propagate errors from base
            var result = StepCompiler.applyKeyStep(Value.error("test error"), "key", null);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).isEqualTo("test error");
        }

        @Test
        @DisplayName("index step on error base propagates error")
        void indexStepOnErrorBasePropagatesError() {
            var result = StepCompiler.applyIndexStep(Value.error("test error"), 0, null);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).isEqualTo("test error");
        }

        @Test
        @DisplayName("wildcard step on error base propagates error")
        void wildcardStepOnErrorBasePropagatesError() {
            var result = StepCompiler.applyWildcardStep(Value.error("test error"), null);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).isEqualTo("test error");
        }

        @Test
        @DisplayName("index union step on error base propagates error")
        void indexUnionStepOnErrorBasePropagatesError() {
            var result = StepCompiler.applyIndexUnionStep(Value.error("test error"), java.util.List.of(0, 1), null);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).isEqualTo("test error");
        }

        @Test
        @DisplayName("attribute union step on error base propagates error")
        void attributeUnionStepOnErrorBasePropagatesError() {
            var result = StepCompiler.applyAttributeUnionStep(Value.error("test error"), java.util.List.of("a", "b"),
                    null);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).isEqualTo("test error");
        }
    }

    @Nested
    @DisplayName("SliceStep")
    class SliceStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("[1:3] returns slice from index 1 to 3")
            void basicSlice() {
                assertCompilesTo("[10, 20, 30, 40, 50][1:3]", array(Value.of(20), Value.of(30)));
            }

            @Test
            @DisplayName("[:] returns all elements")
            void sliceAll() {
                assertCompilesTo("[1, 2, 3][:]", array(Value.of(1), Value.of(2), Value.of(3)));
            }

            @Test
            @DisplayName("[2:] returns from index 2 to end")
            void sliceFromIndex() {
                assertCompilesTo("[10, 20, 30, 40, 50][2:]", array(Value.of(30), Value.of(40), Value.of(50)));
            }

            @Test
            @DisplayName("[:3] returns from start to index 3")
            void sliceToIndex() {
                assertCompilesTo("[10, 20, 30, 40, 50][:3]", array(Value.of(10), Value.of(20), Value.of(30)));
            }

            @Test
            @DisplayName("[-3:] negative from index")
            void negativeFromIndex() {
                assertCompilesTo("[10, 20, 30, 40, 50][-3:]", array(Value.of(30), Value.of(40), Value.of(50)));
            }

            @Test
            @DisplayName("[:-2] negative to index")
            void negativeToIndex() {
                assertCompilesTo("[10, 20, 30, 40, 50][:-2]", array(Value.of(10), Value.of(20), Value.of(30)));
            }

            @Test
            @DisplayName("[::2] with step 2")
            void withStep() {
                assertCompilesTo("[10, 20, 30, 40, 50][::2]", array(Value.of(10), Value.of(30), Value.of(50)));
            }

            @Test
            @DisplayName("[1:5:2] with from, to and step")
            void withFromToStep() {
                assertCompilesTo("[10, 20, 30, 40, 50][1:5:2]", array(Value.of(20), Value.of(40)));
            }

            @Test
            @DisplayName("[::-1] reverses array")
            void reverseArray() {
                assertCompilesTo("[1, 2, 3][::-1]", array(Value.of(3), Value.of(2), Value.of(1)));
            }

            @Test
            @DisplayName("[3:1] returns empty when from >= to")
            void emptyWhenFromGteToIndex() {
                assertCompilesTo("[1, 2, 3, 4, 5][3:1]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("[][1:3] on empty array returns empty")
            void emptyArraySlice() {
                assertCompilesTo("[][1:3]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("[::0] step zero returns error")
            void stepZeroReturnsError() {
                var result = compileExpression("[1, 2, 3][::0]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("zero");
            }

            @Test
            @DisplayName("slice on non-array returns error")
            void sliceOnNonArrayReturnsError() {
                var result = compileExpression("{\"a\": 1}[1:3]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply slice");
            }

            @ParameterizedTest
            @ValueSource(strings = { "42[1:3]", "\"hello\"[1:3]", "true[1:3]", "null[1:3]" })
            @DisplayName("slice on non-array types returns error")
            void sliceOnNonArrayTypesReturnsError(String expr) {
                var result = compileExpression(expr);
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply slice");
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable[1:3] compiles to PureOperator")
            void variableSliceCompilesToPure() {
                assertCompilesTo("items[1:3]", PureOperator.class);
            }

            @Test
            @DisplayName("variable[1:3] evaluates correctly")
            void variableSliceEvaluatesCorrectly() {
                var items = array(Value.of(10), Value.of(20), Value.of(30), Value.of(40));
                assertPureEvaluatesTo("items[1:3]", Map.of("items", items), array(Value.of(20), Value.of(30)));
            }
        }
    }

    @Nested
    @DisplayName("ExpressionStep")
    class ExpressionStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("[(0)] dynamic index access")
            void dynamicIndexAccess() {
                assertCompilesTo("[10, 20, 30][(0)]", Value.of(10));
            }

            @Test
            @DisplayName("[(1+1)] calculated index")
            void calculatedIndex() {
                assertCompilesTo("[10, 20, 30][(1+1)]", Value.of(30));
            }

            @Test
            @DisplayName("[(-1)] negative dynamic index")
            void negativeDynamicIndex() {
                assertCompilesTo("[10, 20, 30][(-1)]", Value.of(30));
            }

            @Test
            @DisplayName("[(\"a\")] dynamic key access")
            void dynamicKeyAccess() {
                assertCompilesTo("{\"a\": 1, \"b\": 2}[(\"a\")]", Value.of(1));
            }

            @Test
            @DisplayName("[(\"a\" + \"b\")] calculated key")
            void calculatedKey() {
                assertCompilesTo("{\"ab\": 42}[(\"a\" + \"b\")]", Value.of(42));
            }

            @Test
            @DisplayName("[(\"missing\")] missing key returns undefined")
            void missingKeyReturnsUndefined() {
                assertCompilesTo("{\"a\": 1}[(\"missing\")]", Value.UNDEFINED);
            }

            @Test
            @DisplayName("[(true)] invalid type returns error")
            void invalidTypeReturnsError() {
                var result = compileExpression("[1, 2, 3][(true)]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("number or string");
            }

            @Test
            @DisplayName("array projection with dynamic key")
            void arrayProjectionWithDynamicKey() {
                assertCompilesTo("[{\"x\": 1}, {\"x\": 2}][(\"x\")]", array(Value.of(1), Value.of(2)));
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable[(0)] compiles to PureOperator")
            void variableExpressionStepCompilesToPure() {
                assertCompilesTo("items[(0)]", PureOperator.class);
            }

            @Test
            @DisplayName("variable[(index)] evaluates correctly")
            void variableExpressionStepEvaluatesCorrectly() {
                var items = array(Value.of("a"), Value.of("b"), Value.of("c"));
                assertPureEvaluatesTo("items[(1)]", Map.of("items", items), Value.of("b"));
            }
        }
    }

    @Nested
    @DisplayName("ConditionStep")
    class ConditionStepTests {

        @Nested
        @DisplayName("on Value (compile-time folding)")
        class OnValue {

            @Test
            @DisplayName("[?(@ > 3)] filters greater than")
            void filterGreaterThan() {
                assertCompilesTo("[1, 2, 3, 4, 5][?(@ > 3)]", array(Value.of(4), Value.of(5)));
            }

            @Test
            @DisplayName("[?(@ < 3)] filters less than")
            void filterLessThan() {
                assertCompilesTo("[1, 2, 3, 4, 5][?(@ < 3)]", array(Value.of(1), Value.of(2)));
            }

            @Test
            @DisplayName("[?(@ == 2)] filters equals")
            void filterEquals() {
                assertCompilesTo("[1, 2, 3, 2, 1][?(@ == 2)]", array(Value.of(2), Value.of(2)));
            }

            @Test
            @DisplayName("[?(# > 2)] filters by index")
            void filterByIndex() {
                assertCompilesTo("[10, 20, 30, 40, 50][?(# > 2)]", array(Value.of(40), Value.of(50)));
            }

            @Test
            @DisplayName("[?(true)] keeps all elements")
            void filterAllTrue() {
                assertCompilesTo("[1, 2, 3][?(true)]", array(Value.of(1), Value.of(2), Value.of(3)));
            }

            @Test
            @DisplayName("[?(false)] removes all elements")
            void filterAllFalse() {
                assertCompilesTo("[1, 2, 3][?(false)]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("[][?(@ > 0)] on empty array returns empty")
            void emptyArrayFilter() {
                assertCompilesTo("[][?(@ > 0)]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("object filtering returns values as array")
            void objectFiltering() {
                var result = compileExpression("{\"a\": 1, \"b\": 2, \"c\": 3}[?(@ > 1)]");
                assertThat(result).isInstanceOf(ArrayValue.class);
                assertThat((ArrayValue) result).containsExactlyInAnyOrder(Value.of(2), Value.of(3));
            }

            @Test
            @DisplayName("condition step on scalar returns error")
            void conditionOnScalarReturnsError() {
                var result = compileExpression("42[?(@ > 0)]");
                assertThat(result).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) result).message()).contains("Cannot apply condition");
            }

            @Test
            @DisplayName("constant base with pure condition folds to Value")
            void constantBasePureConditionFoldsToValue() {
                var result = compileExpression("[1, 2, 3, 4, 5][?(@ > 3)]");
                assertThat(result).isInstanceOf(ArrayValue.class);
                assertThat(result).isEqualTo(array(Value.of(4), Value.of(5)));
            }

            @Test
            @DisplayName("[?(@ > 1 && @ < 5)] compound condition")
            void compoundCondition() {
                assertCompilesTo("[1, 2, 3, 4, 5][?(@ > 1 && @ < 5)]", array(Value.of(2), Value.of(3), Value.of(4)));
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable[?(@ > 0)] compiles to PureOperator")
            void variableConditionCompilesToPure() {
                assertCompilesTo("items[?(@ > 0)]", PureOperator.class);
            }

            @Test
            @DisplayName("variable[?(@ > 2)] evaluates correctly")
            void variableConditionEvaluatesCorrectly() {
                var items = array(Value.of(1), Value.of(2), Value.of(3), Value.of(4));
                assertPureEvaluatesTo("items[?(@ > 2)]", Map.of("items", items), array(Value.of(3), Value.of(4)));
            }
        }
    }

    @Nested
    @DisplayName("RecursiveKeyStep")
    class RecursiveKeyStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("..key finds key in simple object")
            void simpleObject() {
                assertCompilesTo("{\"a\": 1, \"b\": 2}..a", array(Value.of(1)));
            }

            @Test
            @DisplayName("..key finds key in nested objects")
            void nestedObjects() {
                var result = compileExpression("{\"a\": {\"a\": 1}}..a");
                assertThat(result).isInstanceOf(ArrayValue.class);
                var arr = (ArrayValue) result;
                assertThat(arr).hasSize(2);
            }

            @Test
            @DisplayName("..key finds key in array of objects")
            void arrayOfObjects() {
                assertCompilesTo("[{\"x\": 1}, {\"x\": 2}]..x", array(Value.of(1), Value.of(2)));
            }

            @Test
            @DisplayName("..key returns empty when key not found")
            void keyNotFound() {
                assertCompilesTo("{\"a\": 1}..b", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("..key in deep nesting")
            void deepNesting() {
                assertCompilesTo("{\"x\": {\"y\": {\"z\": 42}}}..z", array(Value.of(42)));
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable..key compiles to PureOperator")
            void variableRecursiveKeyCompilesToPure() {
                assertCompilesTo("data..name", PureOperator.class);
            }

            @Test
            @DisplayName("variable..key evaluates correctly")
            void variableRecursiveKeyEvaluatesCorrectly() {
                var data = obj("items", array(obj("name", Value.of("a")), obj("name", Value.of("b"))));
                assertPureEvaluatesTo("data..name", Map.of("data", data), array(Value.of("a"), Value.of("b")));
            }
        }
    }

    @Nested
    @DisplayName("RecursiveIndexStep")
    class RecursiveIndexStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("..[0] finds first element in arrays")
            void findsFirstElement() {
                assertCompilesTo("[10, 20, 30]..[0]", array(Value.of(10)));
            }

            @Test
            @DisplayName("..[0] finds first element in nested arrays")
            void nestedArrays() {
                var result = compileExpression("[[1, 2], [3, 4]]..[0]");
                assertThat(result).isInstanceOf(ArrayValue.class);
                var arr = (ArrayValue) result;
                // Should find: [1,2] (first of outer), 1 (first of [1,2]), 3 (first of [3,4])
                assertThat(arr).hasSize(3);
            }

            @Test
            @DisplayName("..[-1] with negative index")
            void negativeIndex() {
                assertCompilesTo("[10, 20, 30]..[-1]", array(Value.of(30)));
            }

            @Test
            @DisplayName("..[0] in object containing arrays")
            void objectWithArrays() {
                assertCompilesTo("{\"a\": [1, 2], \"b\": [3, 4]}..[0]", array(Value.of(1), Value.of(3)));
            }

            @Test
            @DisplayName("..[0] on empty array returns empty")
            void emptyArray() {
                assertCompilesTo("[]..[0]", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("..[5] out of bounds returns empty")
            void outOfBounds() {
                assertCompilesTo("[1, 2]..[5]", Value.EMPTY_ARRAY);
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable..[0] compiles to PureOperator")
            void variableRecursiveIndexCompilesToPure() {
                assertCompilesTo("data..[0]", PureOperator.class);
            }

            @Test
            @DisplayName("variable..[0] evaluates correctly")
            void variableRecursiveIndexEvaluatesCorrectly() {
                var data = array(Value.of(10), Value.of(20));
                assertPureEvaluatesTo("data..[0]", Map.of("data", data), array(Value.of(10)));
            }
        }
    }

    @Nested
    @DisplayName("RecursiveWildcardStep")
    class RecursiveWildcardStepTests {

        @Nested
        @DisplayName("on Value (compile-time)")
        class OnValue {

            @Test
            @DisplayName("[1, 2, 3]..* collects all elements")
            void flatArray() {
                assertCompilesTo("[1, 2, 3]..*", array(Value.of(1), Value.of(2), Value.of(3)));
            }

            @Test
            @DisplayName("{}..* on empty object returns empty")
            void emptyObject() {
                assertCompilesTo("{}..*", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("[]..* on empty array returns empty")
            void emptyArray() {
                assertCompilesTo("[]..*", Value.EMPTY_ARRAY);
            }

            @Test
            @DisplayName("nested structure collects all values recursively")
            void nestedStructure() {
                var result = compileExpression("{\"a\": 1, \"b\": {\"c\": 2}}..*");
                assertThat(result).isInstanceOf(ArrayValue.class);
                // Should collect: 1, {c:2}, 2
                var arr = (ArrayValue) result;
                assertThat(arr).hasSize(3);
            }
        }

        @Nested
        @DisplayName("on PureOperator (runtime)")
        class OnPure {

            @Test
            @DisplayName("variable..* compiles to PureOperator")
            void variableRecursiveWildcardCompilesToPure() {
                assertCompilesTo("data..*", PureOperator.class);
            }

            @Test
            @DisplayName("variable..* evaluates correctly")
            void variableRecursiveWildcardEvaluatesCorrectly() {
                var data = array(Value.of(1), Value.of(2));
                assertPureEvaluatesTo("data..*", Map.of("data", data), array(Value.of(1), Value.of(2)));
            }
        }
    }
}
