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

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.util.Maybe;
import lombok.val;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared fixtures and helpers used by the end-to-end content-filter tests
 * across both providers. Centralises the {@link SignalType} constants,
 * decision-builder, and {@link EnforcementResult} unwrapping.
 */
final class ContentFilterTestSupport {

    static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    static final SignalType OUTPUT_LIST_TYPE     = Signal.OutputSignal.typeFor(List.class);
    static final SignalType OUTPUT_CITIZEN_TYPE  = Signal.OutputSignal.typeFor(WatchCitizen.class);
    static final SignalType OUTPUT_FLUX_TYPE     = Signal.OutputSignal.typeFor(Flux.class);
    static final SignalType DECISION_SIGNAL_TYPE = Signal.DecisionSignal.TYPE;

    private ContentFilterTestSupport() {
    }

    static AuthorizationDecision permitWith(String obligationsJson) {
        return new AuthorizationDecision(Decision.PERMIT, (ArrayValue) MAPPER.readValue(obligationsJson, Value.class),
                Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    static <T> T expect(EnforcementResult<?> result, Class<T> expectedType) {
        if (!(result.value() instanceof Maybe.Present<?>(var v))) {
            throw new AssertionError("Expected Present value, got " + result.value());
        }
        if (!expectedType.isInstance(v)) {
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName() + ", got " + v.getClass().getSimpleName());
        }
        return expectedType.cast(v);
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> expectList(EnforcementResult<?> result) {
        return (List<T>) expect(result, List.class);
    }

    @SuppressWarnings("unchecked")
    static <T> Flux<T> expectFlux(EnforcementResult<?> result) {
        return (Flux<T>) expect(result, Flux.class);
    }

    static List<WatchCitizen> theUsualSuspects() {
        val list = new ArrayList<WatchCitizen>();
        list.add(new WatchCitizen("Sam Vimes", "Watch", "Scoone Avenue", 50, "Cabal of the Iron Hand"));
        list.add(new WatchCitizen("Carrot Ironfoundersson", "Watch", "Treacle Mine Road", 30, null));
        list.add(new WatchCitizen("Tiffany Aching", "Witches", "The Chalk", 16, null));
        list.add(new WatchCitizen("Cohen the Barbarian", "Heroes", "The Steppes", 90, "Silver Horde"));
        list.add(new WatchCitizen("Rincewind", "Wizards", "Unseen University", 33, "Coward's Anonymous"));
        return list;
    }

    /**
     * Test fixture: a record used in scenarios that exercise content filtering on
     * typed POJOs.
     */
    record WatchCitizen(String name, String guild, String address, int age, String secretSocietyMembership) {}
}
