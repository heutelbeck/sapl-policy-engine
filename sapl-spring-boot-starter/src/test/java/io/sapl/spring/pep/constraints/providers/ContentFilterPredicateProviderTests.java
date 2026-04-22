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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintType;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ContentFilterPredicateProvider")
class ContentFilterPredicateProviderTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final SignalType OUTPUT_MAP_TYPE = new ValueSignalType<Map>(
            (Class<? extends Signal.ValueSignal<Map>>) (Class) Signal.OutputSignal.class, Map.class);

    private static final SignalType DECISION_SIGNAL_TYPE = new ValueSignalType<>(Signal.DecisionSignal.class,
            AuthorizationDecision.class);

    private static final SignalType CANCEL_SIGNAL_TYPE = new SignalType.VoidSignalType(Signal.CancelSignal.class);

    private final ContentFilterPredicateProvider provider = new ContentFilterPredicateProvider(MAPPER);

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @Nested
    @DisplayName("Responsibility")
    class Responsibility {

        @Test
        @DisplayName("non-matching constraint type yields empty Optional")
        void givenWrongConstraintTypeThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "somethingElse"}
                    """), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("non-object constraint yields empty Optional")
        void givenNonObjectConstraintThenEmpty() {
            val result = provider.getConstraintHandler(v("\"plain string\""), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("the filterJsonContent constraint is not claimed (different provider's responsibility)")
        void givenFilterJsonContentConstraintThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "filterJsonContent"}
                    """), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matching constraint but no OutputSignal supported yields empty Optional")
        void givenMatchingConstraintWithoutOutputSignalThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "jsonContentFilterPredicate"}
                    """), Set.of(DECISION_SIGNAL_TYPE, CANCEL_SIGNAL_TYPE));
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Successful resolution")
    class SuccessfulResolution {

        @Test
        @DisplayName("returns Mapper at the supported OutputSignal with default priority 10")
        void givenMatchingConstraintAndOutputSignalThenReturnsMapper() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "jsonContentFilterPredicate"}
                    """), Set.of(OUTPUT_MAP_TYPE, DECISION_SIGNAL_TYPE));

            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(OUTPUT_MAP_TYPE);
                assertThat(s.priority()).isEqualTo(10);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
        }

        @Test
        @DisplayName("priority is lower than ContentFilteringProvider so filtering runs before transformation")
        void priorityRunsBeforeContentFiltering() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "jsonContentFilterPredicate"}
                    """), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped.priority())
                    .as("predicate priority must be < ContentFilteringProvider priority").isLessThan(30));
        }

        @Test
        @DisplayName("returned mapper drops list elements that do not match the predicate")
        @SuppressWarnings("unchecked")
        void givenResolvedMapperWhenAppliedToListThenFiltersElements() {
            val result = provider.getConstraintHandler(v("""
                    {
                      "type": "jsonContentFilterPredicate",
                      "conditions": [{"path": "$.age", "type": ">=", "value": 18}]
                    }
                    """), Set.of(OUTPUT_MAP_TYPE));

            val mapper  = (Mapper<Object>) result.orElseThrow().handler();
            val payload = List.of(new HashMap<>(Map.of("name", "Alice", "age", 30)),
                    new HashMap<>(Map.of("name", "Bob", "age", 12)), new HashMap<>(Map.of("name", "Carol", "age", 45)));
            val output  = (List<Map<String, Object>>) mapper.apply(payload);
            assertThat(output).hasSize(2).extracting(map -> map.get("name")).containsExactly("Alice", "Carol");
        }

        @Test
        @DisplayName("returned mapper drops a scalar payload to null when the predicate does not match")
        void givenResolvedMapperWhenAppliedToScalarNonMatchThenReturnsNull() {
            val result = provider.getConstraintHandler(v("""
                    {
                      "type": "jsonContentFilterPredicate",
                      "conditions": [{"path": "$.age", "type": ">=", "value": 18}]
                    }
                    """), Set.of(OUTPUT_MAP_TYPE));

            val mapper  = (Mapper<Object>) result.orElseThrow().handler();
            val payload = new HashMap<>(Map.of("name", "Eve", "age", 12));
            assertThat(mapper.apply(payload)).isNull();
        }

        @Test
        @DisplayName("returned mapper passes through a scalar payload that matches the predicate")
        void givenResolvedMapperWhenAppliedToScalarMatchThenReturnsPayload() {
            val result = provider.getConstraintHandler(v("""
                    {
                      "type": "jsonContentFilterPredicate",
                      "conditions": [{"path": "$.age", "type": ">=", "value": 18}]
                    }
                    """), Set.of(OUTPUT_MAP_TYPE));

            val mapper  = (Mapper<Object>) result.orElseThrow().handler();
            val payload = new HashMap<>(Map.of("name", "Alice", "age", 30));
            assertThat(mapper.apply(payload)).isSameAs(payload);
        }

        @Test
        @DisplayName("ConstraintType is not set on the scoped handler (assigned by planner)")
        void resolvedHandlerHasNoConstraintType() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "jsonContentFilterPredicate"}
                    """), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped.handler()).isInstanceOf(Mapper.class));
            assertThat(ConstraintType.OBLIGATION).as("ConstraintType is set later by the planner, not the provider")
                    .isNotNull();
        }
    }
}
