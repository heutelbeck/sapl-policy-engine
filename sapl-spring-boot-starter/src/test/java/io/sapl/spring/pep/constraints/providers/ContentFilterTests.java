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
package io.sapl.spring.pep.constraints.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import org.springframework.security.access.AccessDeniedException;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ContentFilter")
class ContentFilterTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @Nested
    @DisplayName("Payload dispatch in getHandler")
    class PayloadDispatch {

        private final Value deleteName = v("""
                {
                  "type": "filterJsonContent",
                  "actions": [{"path": "$.name", "type": "delete"}]
                }
                """);

        @Test
        @DisplayName("null payload returns null")
        void givenNullPayloadThenReturnsNull() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            assertThat(handler.apply(null)).isNull();
        }

        @Test
        @DisplayName("scalar map payload is filtered as a single element")
        void givenScalarMapPayloadThenFilteredAsElement() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            assertThat(handler.apply(new HashMap<>(Map.of("name", "Alice", "age", 30))))
                    .isInstanceOfSatisfying(Map.class, m -> assertThat(m).doesNotContainKey("name"));
        }

        @Test
        @DisplayName("Optional payload with present value is filtered elementwise")
        @SuppressWarnings("unchecked")
        void givenOptionalPresentThenFilteredElementwise() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (Optional<Map<String, Object>>) handler
                    .apply(Optional.of(new HashMap<>(Map.of("name", "Alice", "age", 30))));
            assertThat(result)
                    .hasValueSatisfying(m -> assertThat(m).doesNotContainKey("name").containsEntry("age", 30));
        }

        @Test
        @DisplayName("Optional empty payload remains empty")
        @SuppressWarnings("unchecked")
        void givenOptionalEmptyThenRemainsEmpty() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (Optional<Object>) handler.apply(Optional.empty());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("List payload is filtered elementwise")
        @SuppressWarnings("unchecked")
        void givenListPayloadThenFilteredElementwise() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (List<Map<String, Object>>) handler
                    .apply(List.of(new HashMap<>(Map.of("name", "Alice", "age", 30)),
                            new HashMap<>(Map.of("name", "Bob", "age", 40))));
            assertThat(result).hasSize(2).allSatisfy(m -> assertThat(m).doesNotContainKey("name").containsKey("age"));
        }

        @Test
        @DisplayName("Set payload is filtered elementwise")
        @SuppressWarnings("unchecked")
        void givenSetPayloadThenFilteredElementwise() {
            val input = new LinkedHashSet<Map<String, Object>>();
            input.add(new HashMap<>(Map.of("name", "Alice", "age", 30)));
            input.add(new HashMap<>(Map.of("name", "Bob", "age", 40)));
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (Set<Map<String, Object>>) handler.apply(input);
            assertThat(result).hasSize(2).allSatisfy(m -> assertThat(m).doesNotContainKey("name"));
        }

        @Test
        @DisplayName("Object array payload is filtered elementwise")
        @SuppressWarnings({ "unchecked", "rawtypes" })
        void givenArrayPayloadThenFilteredElementwise() {
            val input   = new Map[] { new HashMap<>(Map.of("name", "Alice", "age", 30)),
                    new HashMap<>(Map.of("name", "Bob", "age", 40)) };
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (Map[]) handler.apply(input);
            assertThat(result).hasSize(2)
                    .allSatisfy(m -> assertThat((Map<String, Object>) m).doesNotContainKey("name"));
        }

        @Test
        @DisplayName("Mono payload is filtered elementwise")
        @SuppressWarnings("unchecked")
        void givenMonoPayloadThenFilteredElementwise() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (Mono<Map<String, Object>>) handler
                    .apply(Mono.just(new HashMap<>(Map.of("name", "Alice", "age", 30))));
            StepVerifier.create(result)
                    .assertNext(element -> assertThat(element).doesNotContainKey("name").containsEntry("age", 30))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Flux payload is filtered elementwise")
        void givenFluxPayloadThenFilteredElementwise() {
            val handler = ContentFilter.getHandler(deleteName, MAPPER);
            val result  = (Flux<?>) handler.apply(Flux.just(new HashMap<>(Map.of("name", "Alice", "age", 30)),
                    new HashMap<>(Map.of("name", "Bob", "age", 40))));
            StepVerifier.create(result).expectNextCount(2).verifyComplete();
        }
    }

    @Nested
    @DisplayName("predicateFromConditions")
    class PredicateConditions {

        @Test
        @DisplayName("missing conditions yields always-true predicate")
        void givenNoConditionsThenAlwaysTrue() {
            val predicate = ContentFilter.predicateFromConditions(v("""
                    {"type": "filterJsonContent"}
                    """), MAPPER);
            assertThat(predicate.test(Map.of("anything", 1))).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("comparisonAndEqualityScenarios")
        @DisplayName("comparison and equality conditions")
        void comparisonAndEqualityCondition(String name, String constraintJson, Object payload, boolean expected) {
            val predicate = ContentFilter.predicateFromConditions(v(constraintJson), MAPPER);
            assertThat(predicate.test(payload)).isEqualTo(expected);
        }

        static Stream<Arguments> comparisonAndEqualityScenarios() {
            return Stream.of(arguments("text-equal matches", """
                    {"conditions": [{"path": "$.name", "type": "==", "value": "Alice"}]}
                    """, Map.of("name", "Alice"), true), arguments("text-equal does not match", """
                    {"conditions": [{"path": "$.name", "type": "==", "value": "Alice"}]}
                    """, Map.of("name", "Bob"), false), arguments("number-equal matches", """
                    {"conditions": [{"path": "$.age", "type": "==", "value": 30}]}
                    """, Map.of("age", 30), true), arguments("number-equal does not match", """
                    {"conditions": [{"path": "$.age", "type": "==", "value": 30}]}
                    """, Map.of("age", 31), false), arguments(">=  matches at boundary", """
                    {"conditions": [{"path": "$.age", "type": ">=", "value": 30}]}
                    """, Map.of("age", 30), true), arguments(">=  rejects below", """
                    {"conditions": [{"path": "$.age", "type": ">=", "value": 30}]}
                    """, Map.of("age", 29), false), arguments("<= matches at boundary", """
                    {"conditions": [{"path": "$.age", "type": "<=", "value": 30}]}
                    """, Map.of("age", 30), true), arguments("<  rejects at boundary", """
                    {"conditions": [{"path": "$.age", "type": "<", "value": 30}]}
                    """, Map.of("age", 30), false), arguments(">  matches above", """
                    {"conditions": [{"path": "$.age", "type": ">", "value": 30}]}
                    """, Map.of("age", 31), true));
        }

        @Test
        @DisplayName("!= negates ==")
        void notEqualsCondition() {
            val predicate = ContentFilter.predicateFromConditions(v("""
                    {"conditions": [{"path": "$.name", "type": "!=", "value": "Alice"}]}
                    """), MAPPER);
            assertThat(predicate).satisfies(p -> {
                assertThat(p.test(Map.of("name", "Bob"))).isTrue();
                assertThat(p.test(Map.of("name", "Alice"))).isFalse();
            });
        }

        @Test
        @DisplayName("=~ regex matches against the value at path")
        void regexCondition() {
            val predicate = ContentFilter.predicateFromConditions(v("""
                    {"conditions": [{"path": "$.email", "type": "=~", "value": "[a-z]+@example\\\\.com"}]}
                    """), MAPPER);
            assertThat(predicate).satisfies(p -> {
                assertThat(p.test(Map.of("email", "alice@example.com"))).isTrue();
                assertThat(p.test(Map.of("email", "alice@other.com"))).isFalse();
            });
        }

        @Test
        @DisplayName("multiple conditions combine via AND")
        void multipleConditionsAreAnded() {
            val predicate = ContentFilter.predicateFromConditions(v("""
                    {
                      "conditions": [
                        {"path": "$.name", "type": "==", "value": "Alice"},
                        {"path": "$.age",  "type": ">=", "value": 18}
                      ]
                    }
                    """), MAPPER);
            assertThat(predicate).satisfies(p -> {
                assertThat(p.test(Map.of("name", "Alice", "age", 30))).isTrue();
                assertThat(p.test(Map.of("name", "Alice", "age", 10))).isFalse();
                assertThat(p.test(Map.of("name", "Bob", "age", 30))).isFalse();
            });
        }

        @Test
        @DisplayName("path absent in payload raises AccessDeniedException at evaluation")
        void pathNotPresentRaisesException() {
            val predicate = ContentFilter.predicateFromConditions(v("""
                    {"conditions": [{"path": "$.missing", "type": "==", "value": "x"}]}
                    """), MAPPER);
            val input     = Map.of("name", "Alice");
            assertThatThrownBy(() -> predicate.test(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("path defined in the constraint is not present");
        }
    }

    @Nested
    @DisplayName("Action transformations")
    class TransformationActions {

        @Test
        @DisplayName("missing actions yields identity transformation")
        void noActionsReturnsIdentity() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"type": "filterJsonContent"}
                    """), MAPPER);
            val input     = new HashMap<>(Map.of("name", "Alice"));
            assertThat(transform.apply(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("delete action removes the field at path")
        void deleteRemovesField() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.name", "type": "delete"}]}
                    """), MAPPER);
            val result    = transform.apply(new HashMap<>(Map.of("name", "Alice", "age", 30)));
            assertThat(result).isInstanceOfSatisfying(Map.class, m -> assertThat(m).doesNotContainKey("name"));
        }

        @Test
        @DisplayName("replace action substitutes the value at path")
        void replaceSubstitutesValue() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.name", "type": "replace", "replacement": "REDACTED"}]}
                    """), MAPPER);
            val result    = transform.apply(new HashMap<>(Map.of("name", "Alice", "age", 30)));
            assertThat(result).isInstanceOfSatisfying(Map.class, m -> assertThat(m).containsEntry("name", "REDACTED"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("blackenScenarios")
        @DisplayName("blacken on $.name")
        void blackenOnName(String name, String constraintJson, String expected) {
            val transform = ContentFilter.getTransformationHandler(v(constraintJson), MAPPER);
            val result    = transform.apply(new HashMap<>(Map.of("name", "Alice")));
            assertThat(result).isInstanceOfSatisfying(Map.class, m -> assertThat(m).containsEntry("name", expected));
        }

        static Stream<Arguments> blackenScenarios() {
            return Stream.of(arguments("default replaces every char with the black square", """
                    {"actions": [{"path": "$.name", "type": "blacken"}]}
                    """, "█████"), arguments("discloseLeft keeps left chars visible", """
                    {"actions": [{"path": "$.name", "type": "blacken", "discloseLeft": 2}]}
                    """, "Al███"), arguments("discloseRight keeps right chars visible", """
                    {"actions": [{"path": "$.name", "type": "blacken", "discloseRight": 2}]}
                    """, "███ce"), arguments("explicit length overrides the replaced range", """
                    {"actions": [{"path": "$.name", "type": "blacken", "length": 3}]}
                    """, "███"), arguments("custom replacement character", """
                    {"actions": [{"path": "$.name", "type": "blacken", "replacement": "*"}]}
                    """, "*****"), arguments("returns original when full disclosure exceeds string length", """
                    {"actions": [{"path": "$.name", "type": "blacken", "discloseLeft": 100}]}
                    """, "Alice"));
        }
    }

    @Nested
    @DisplayName("Constraint validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("non-object constraint is rejected")
        void nonObjectConstraintRejected() {
            val constraint = v("\"plain string\"");
            assertThatThrownBy(() -> ContentFilter.predicateFromConditions(constraint, MAPPER))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Expected an object value");
        }

        @Test
        @DisplayName("conditions not an array is rejected")
        void conditionsNotArrayRejected() {
            val constraint = v("""
                    {"conditions": "not-an-array"}
                    """);
            assertThatThrownBy(() -> ContentFilter.predicateFromConditions(constraint, MAPPER))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("'conditions' not an array");
        }

        @Test
        @DisplayName("condition without path is rejected")
        void conditionWithoutPathRejected() {
            val constraint = v("""
                    {"conditions": [{"type": "==", "value": "x"}]}
                    """);
            assertThatThrownBy(() -> ContentFilter.predicateFromConditions(constraint, MAPPER))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Not a valid predicate condition");
        }

        @Test
        @DisplayName("condition with unknown type is rejected")
        void conditionWithUnknownTypeRejected() {
            val constraint = v("""
                    {"conditions": [{"path": "$.x", "type": "??", "value": 1}]}
                    """);
            assertThatThrownBy(() -> ContentFilter.predicateFromConditions(constraint, MAPPER))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Not a valid predicate condition");
        }

        @Test
        @DisplayName("dangerous regex is rejected")
        void dangerousRegexRejected() {
            val constraint = v("""
                    {"conditions": [{"path": "$.x", "type": "=~", "value": "(a+)+"}]}
                    """);
            assertThatThrownBy(() -> ContentFilter.predicateFromConditions(constraint, MAPPER))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Unsafe regex pattern");
        }

        @Test
        @DisplayName("actions not an array is rejected")
        void actionsNotArrayRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": "not-an-array"}
                    """), MAPPER);
            val input     = Map.of("x", 1);
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("'actions' is not an array");
        }

        @Test
        @DisplayName("non-object action is rejected")
        void nonObjectActionRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": ["not-an-object"]}
                    """), MAPPER);
            val input     = Map.of("x", 1);
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not an object");
        }

        @Test
        @DisplayName("unknown action type is rejected")
        void unknownActionTypeRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.x", "type": "unknown"}]}
                    """), MAPPER);
            val input     = new HashMap<>(Map.of("x", 1));
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Unknown action type");
        }

        @Test
        @DisplayName("blacken on non-textual is rejected")
        void blackenOnNonTextualRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.x", "type": "blacken"}]}
                    """), MAPPER);
            val input     = new HashMap<>(Map.of("x", 42));
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not a text note");
        }

        @Test
        @DisplayName("blacken length not a number is rejected")
        void blackenLengthNotNumberRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.x", "type": "blacken", "length": "five"}]}
                    """), MAPPER);
            val input     = new HashMap<>(Map.of("x", "Alice"));
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("'length'");
        }

        @Test
        @DisplayName("replace without replacement is rejected")
        void replaceWithoutReplacementRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.x", "type": "replace"}]}
                    """), MAPPER);
            val input     = new HashMap<>(Map.of("x", "Alice"));
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("does not specify a 'replacement'");
        }

        @Test
        @DisplayName("action path not present in payload is rejected")
        void actionPathNotPresentRejected() {
            val transform = ContentFilter.getTransformationHandler(v("""
                    {"actions": [{"path": "$.missing", "type": "delete"}]}
                    """), MAPPER);
            val input     = new HashMap<>(Map.of("x", 1));
            assertThatThrownBy(() -> transform.apply(input)).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("path defined in the constraint is not present");
        }
    }
}
