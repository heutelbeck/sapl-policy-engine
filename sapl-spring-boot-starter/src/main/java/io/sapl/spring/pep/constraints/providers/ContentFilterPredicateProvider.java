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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;

import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.RequiredArgsConstructor;
import lombok.val;
import tools.jackson.databind.ObjectMapper;

/**
 * Translates a {@code jsonContentFilterPredicate} constraint into a
 * {@link Mapper} attached to the PEP's
 * {@link Signal.OutputSignal}. Claims responsibility only for OutputSignals
 * whose value type is a
 * {@link List}, {@link Set}, {@link Optional}, {@link Publisher} (e.g.
 * {@code Mono}, {@code Flux}), or
 * array. Scalar types and other collection families (e.g. {@code Queue},
 * {@code Deque}, {@code Map}) are
 * rejected with {@link Optional#empty()} so the planner can produce a clear
 * failure substitute rather than
 * silently returning {@code null}.
 * <p>
 * Note: a {@code List} payload is filtered into an {@code ArrayList} and a
 * {@code Set} payload into a
 * {@code HashSet}, regardless of the input subtype. Callers that need a
 * specific subtype (for example
 * {@code LinkedList} ordering or {@code TreeSet} comparators) must declare the
 * broader interface in their
 * method signature.
 */
@RequiredArgsConstructor
public class ContentFilterPredicateProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE = "jsonContentFilterPredicate";

    private static final int DEFAULT_PRIORITY = 10;

    private final ObjectMapper objectMapper;

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintHandlerProvider.constraintIsOfType(constraint, CONSTRAINT_TYPE)) {
            return List.of();
        }
        return SignalType.findIn(supportedSignals, Signal.OutputSignal.class)
                .filter(outputSignal -> isContainer(outputSignal.valueType())).map(outputSignal -> {
                    val mapper = ContentFilter.getFilterPredicateHandler(constraint, objectMapper);
                    return List.of(new ScopedConstraintHandler(mapper, outputSignal, DEFAULT_PRIORITY));
                }).orElseGet(List::of);
    }

    private static boolean isContainer(ResolvableType type) {
        val raw = type.toClass();
        return type.isArray() || List.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw)
                || Optional.class.isAssignableFrom(raw) || Publisher.class.isAssignableFrom(raw);
    }
}
