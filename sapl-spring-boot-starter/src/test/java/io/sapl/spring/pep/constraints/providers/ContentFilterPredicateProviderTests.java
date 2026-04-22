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

import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.DECISION_SIGNAL_TYPE;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.OUTPUT_FLUX_TYPE;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.OUTPUT_LIST_TYPE;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.expectFlux;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.permitWith;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.theUsualSuspects;
import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.WatchCitizen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.EnforcementExecutor;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ContentFilterPredicateProvider")
class ContentFilterPredicateProviderTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final SignalType OUTPUT_MAP_TYPE = new ValueSignalType<Map>(
            (Class<? extends Signal.ValueSignal<Map>>) (Class) Signal.OutputSignal.class, Map.class);

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
                    """), Set.of(OUTPUT_LIST_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("non-object constraint yields empty Optional")
        void givenNonObjectConstraintThenEmpty() {
            val result = provider.getConstraintHandler(v("\"plain string\""), Set.of(OUTPUT_LIST_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("the filterJsonContent constraint is not claimed (different provider's responsibility)")
        void givenFilterJsonContentConstraintThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "filterJsonContent"}
                    """), Set.of(OUTPUT_LIST_TYPE));
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

        @Test
        @DisplayName("OutputSignal carries a scalar value type: predicate has no element-filter semantics, empty Optional")
        void givenScalarOutputSignalThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "jsonContentFilterPredicate"}
                    """), Set.of(OUTPUT_MAP_TYPE, DECISION_SIGNAL_TYPE));
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
                    """), Set.of(OUTPUT_LIST_TYPE, DECISION_SIGNAL_TYPE));

            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(OUTPUT_LIST_TYPE);
                assertThat(s.priority()).isEqualTo(10);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
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
                    """), Set.of(OUTPUT_LIST_TYPE));

            val mapper  = (Mapper<Object>) result.orElseThrow().handler();
            val payload = List.of(new HashMap<>(Map.of("name", "Alice", "age", 30)),
                    new HashMap<>(Map.of("name", "Bob", "age", 12)), new HashMap<>(Map.of("name", "Carol", "age", 45)));
            val output  = (List<Map<String, Object>>) mapper.apply(payload);
            assertThat(output).hasSize(2).extracting(map -> map.get("name")).containsExactly("Alice", "Carol");
        }
    }

    @Nested
    @DisplayName("End-to-end scenarios")
    class EndToEndScenarios {

        private final EnforcementPlanner  planner  = new EnforcementPlanner(
                List.of(new ContentFilterPredicateProvider(MAPPER)), MAPPER);
        private final EnforcementExecutor executor = new EnforcementExecutor();

        @Test
        @DisplayName("Filtering a list does not mutate the original list")
        void givenFilteringWhenAppliedThenInputUnchanged() {
            val original      = theUsualSuspects();
            val originalNames = original.stream().map(WatchCitizen::name).toList();

            val decision = permitWith("""
                    [{ "type": "jsonContentFilterPredicate",
                       "conditions": [{"path": "$.guild", "type": "==", "value": "Watch"}] }]
                    """);
            val plan     = planner.plan(decision, Set.of(OUTPUT_LIST_TYPE, DECISION_SIGNAL_TYPE));
            executor.execute(plan, new Signal.OutputSignal<>(List.class, original), false);

            assertThat(original).extracting(WatchCitizen::name).containsExactlyElementsOf(originalNames);
        }

        @Test
        @DisplayName("Citizens flowing through a Flux are emitted only when they match the predicate")
        void givenFluxOfCitizensThenFilteredAsStream() {
            val decision = permitWith("""
                    [{ "type": "jsonContentFilterPredicate",
                       "conditions": [{"path": "$.age", "type": ">=", "value": 18}] }]
                    """);
            val plan     = planner.plan(decision, Set.of(OUTPUT_FLUX_TYPE, DECISION_SIGNAL_TYPE));
            val source   = Flux.fromIterable(theUsualSuspects());
            val result   = executor.execute(plan, new Signal.OutputSignal<>(Flux.class, source), false);

            Flux<WatchCitizen> filteredFlux = expectFlux(result);
            StepVerifier.create(filteredFlux).assertNext(c -> assertThat(c.name()).isEqualTo("Sam Vimes"))
                    .assertNext(c -> assertThat(c.name()).isEqualTo("Carrot Ironfoundersson"))
                    .assertNext(c -> assertThat(c.name()).isEqualTo("Cohen the Barbarian"))
                    .assertNext(c -> assertThat(c.name()).isEqualTo("Rincewind")).verifyComplete();
        }
    }
}
