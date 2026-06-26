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
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.OUTPUT_CITIZEN_TYPE;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.expect;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.permitWith;
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
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ContentFilteringProvider")
class ContentFilteringProviderTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final SignalType OUTPUT_MAP_TYPE    = Signal.OutputSignal.typeFor(Map.class);
    private static final SignalType CANCEL_SIGNAL_TYPE = new SignalType.VoidSignalType(Signal.CancelSignal.class);

    private final ContentFilteringProvider provider = new ContentFilteringProvider(MAPPER);

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @Nested
    @DisplayName("Responsibility")
    class Responsibility {

        @Test
        @DisplayName("non-matching constraint type yields empty Optional")
        void givenWrongConstraintTypeThenEmpty() {
            val result = provider.getConstraintHandlers(v("""
                    {"type": "somethingElse"}
                    """), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("non-object constraint yields empty Optional")
        void givenNonObjectConstraintThenEmpty() {
            val result = provider.getConstraintHandlers(v("\"plain string\""), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matching constraint but no OutputSignal supported yields empty Optional")
        void givenMatchingConstraintWithoutOutputSignalThenEmpty() {
            val result = provider.getConstraintHandlers(v("""
                    {"type": "filterJsonContent"}
                    """), Set.of(DECISION_SIGNAL_TYPE, CANCEL_SIGNAL_TYPE));
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Successful resolution")
    class SuccessfulResolution {

        @Test
        @DisplayName("returns Mapper at the supported OutputSignal with default priority")
        void givenMatchingConstraintAndOutputSignalThenReturnsMapper() {
            val result = provider.getConstraintHandlers(v("""
                    {"type": "filterJsonContent"}
                    """), Set.of(OUTPUT_MAP_TYPE, DECISION_SIGNAL_TYPE));

            assertThat(result).singleElement().satisfies(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(OUTPUT_MAP_TYPE);
                assertThat(s.priority()).isEqualTo(30);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
        }

        @Test
        @DisplayName("returned mapper executes the configured filter actions")
        @SuppressWarnings("unchecked")
        void givenResolvedMapperWhenAppliedThenFiltersPayload() {
            val result = provider.getConstraintHandlers(v("""
                    {
                      "type": "filterJsonContent",
                      "actions": [{"path": "$.name", "type": "delete"}]
                    }
                    """), Set.of(OUTPUT_MAP_TYPE));

            val mapper  = (Mapper<Object>) result.getFirst().handler();
            val payload = new HashMap<>(Map.of("name", "Alice", "age", 30));
            val output  = (Map<String, Object>) mapper.apply(payload);
            assertThat(output).doesNotContainKey("name").containsEntry("age", 30);
        }

    }

    @Nested
    @DisplayName("End-to-end scenarios")
    class EndToEndScenarios {

        private final EnforcementPlanner planner = new EnforcementPlanner(List.of(new ContentFilteringProvider(MAPPER)),
                MAPPER);

        @Test
        @DisplayName("Redacting a single record returns a new instance; the original record is unchanged")
        void givenContentFilterOnRecordThenInputUnchanged() {
            val original = new WatchCitizen("Sam Vimes", "Watch", "Scoone Avenue", 50, "Cabal of the Iron Hand");
            val decision = permitWith("""
                    [{ "type": "filterJsonContent",
                       "actions": [{"path": "$.address", "type": "blacken"}] }]
                    """);
            val plan     = planner.plan(decision, Set.of(OUTPUT_CITIZEN_TYPE, DECISION_SIGNAL_TYPE));
            plan.execute(OutputSignal.of(WatchCitizen.class, original), false);

            assertThat(original.address()).isEqualTo("Scoone Avenue");
        }

        @Test
        @DisplayName("Three sequential invocations of one plan produce three independent results")
        void givenOnePlanWhenAppliedManyTimesThenEachInvocationIsIndependent() {
            val decision = permitWith("""
                    [{ "type": "filterJsonContent",
                       "actions": [{"path": "$.secretSocietyMembership", "type": "delete"}] }]
                    """);
            val plan     = planner.plan(decision, Set.of(OUTPUT_CITIZEN_TYPE, DECISION_SIGNAL_TYPE));

            val vimes     = expect(
                    plan.execute(OutputSignal.of(WatchCitizen.class,
                            new WatchCitizen("Vimes", "Watch", "Scoone Avenue", 50, "Cabal")), false),
                    WatchCitizen.class);
            val cohen     = expect(
                    plan.execute(OutputSignal.of(WatchCitizen.class,
                            new WatchCitizen("Cohen", "Heroes", "Steppes", 90, "Silver Horde")), false),
                    WatchCitizen.class);
            val rincewind = expect(
                    plan.execute(OutputSignal.of(WatchCitizen.class,
                            new WatchCitizen("Rincewind", "Wizards", "UU", 33, "Coward's Anonymous")), false),
                    WatchCitizen.class);

            assertThat(vimes).satisfies(c -> {
                assertThat(c.name()).isEqualTo("Vimes");
                assertThat(c.secretSocietyMembership()).isNull();
            });
            assertThat(cohen).satisfies(c -> {
                assertThat(c.name()).isEqualTo("Cohen");
                assertThat(c.secretSocietyMembership()).isNull();
            });
            assertThat(rincewind).satisfies(c -> {
                assertThat(c.name()).isEqualTo("Rincewind");
                assertThat(c.secretSocietyMembership()).isNull();
            });
        }

        @Test
        @DisplayName("An obligation referring to a missing path fails; subsequent obligations still execute")
        void givenOneFailingObligationThenFailureStateSet() {
            val decision = permitWith("""
                    [
                      { "type": "filterJsonContent",
                        "actions": [{"path": "$.nonexistent", "type": "delete"}] },
                      { "type": "filterJsonContent",
                        "actions": [{"path": "$.address", "type": "blacken"}] }
                    ]
                    """);
            val plan     = planner.plan(decision, Set.of(OUTPUT_CITIZEN_TYPE, DECISION_SIGNAL_TYPE));
            val result   = plan.execute(OutputSignal.of(WatchCitizen.class,
                    new WatchCitizen("Sam Vimes", "Watch", "Scoone Avenue", 50, null)), false);

            assertThat(result.failureState()).isTrue();
        }
    }
}
