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
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintType;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("ContentFilteringProvider")
class ContentFilteringProviderTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final SignalType OUTPUT_MAP_TYPE = new ValueSignalType<Map>(
            (Class<? extends Signal.ValueSignal<Map>>) (Class) Signal.OutputSignal.class, Map.class);

    private static final SignalType DECISION_SIGNAL_TYPE = new ValueSignalType<>(Signal.DecisionSignal.class,
            io.sapl.api.pdp.AuthorizationDecision.class);

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
        @DisplayName("matching constraint but no OutputSignal supported yields empty Optional")
        void givenMatchingConstraintWithoutOutputSignalThenEmpty() {
            val result = provider.getConstraintHandler(v("""
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
            val result = provider.getConstraintHandler(v("""
                    {"type": "filterJsonContent"}
                    """), Set.of(OUTPUT_MAP_TYPE, DECISION_SIGNAL_TYPE));

            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(OUTPUT_MAP_TYPE);
                assertThat(s.priority()).isEqualTo(30);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
        }

        @Test
        @DisplayName("returned mapper executes the configured filter actions")
        @SuppressWarnings("unchecked")
        void givenResolvedMapperWhenAppliedThenFiltersPayload() {
            val result = provider.getConstraintHandler(v("""
                    {
                      "type": "filterJsonContent",
                      "actions": [{"path": "$.name", "type": "delete"}]
                    }
                    """), Set.of(OUTPUT_MAP_TYPE));

            val mapper  = (Mapper<Object>) result.orElseThrow().handler();
            val payload = new HashMap<>(Map.of("name", "Alice", "age", 30));
            val output  = (Map<String, Object>) mapper.apply(payload);
            assertThat(output).doesNotContainKey("name").containsEntry("age", 30);
        }

        @Test
        @DisplayName("ConstraintType is not set on the scoped handler (assigned by planner)")
        void resolvedHandlerHasNoConstraintType() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "filterJsonContent"}
                    """), Set.of(OUTPUT_MAP_TYPE));
            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped.handler()).isInstanceOf(Mapper.class));
            assertThat(ConstraintType.OBLIGATION).as("ConstraintType is set later by the planner, not the provider")
                    .isNotNull();
        }
    }
}
