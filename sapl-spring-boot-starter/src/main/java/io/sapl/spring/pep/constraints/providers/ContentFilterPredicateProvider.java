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

import java.util.Optional;
import java.util.Set;

import io.sapl.api.model.Value;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
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
 * {@link Signal.OutputSignal}. The mapper applies the constraint's predicate as
 * an element filter, dropping
 * non-matching elements from collection-shaped payloads
 * ({@link java.util.Optional}, {@link java.util.List},
 * {@link java.util.Set}, {@link reactor.core.publisher.Mono},
 * {@link reactor.core.publisher.Flux},
 * {@link Object Object[]}). Scalar payloads are dropped to {@code null} when
 * the predicate does not match.
 * <p>
 * Returns {@link Optional#empty()} when the PEP does not fire an OutputSignal.
 */
@RequiredArgsConstructor
public class ContentFilterPredicateProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE = "jsonContentFilterPredicate";

    private static final int DEFAULT_PRIORITY = 10;

    private final ObjectMapper objectMapper;

    @Override
    public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE)) {
            return Optional.empty();
        }
        return OutputSignals.findIn(supportedSignals).map(outputSignal -> {
            val mapper = ContentFilter.getFilterPredicateHandler(constraint, outputSignal.valueType(), objectMapper);
            return new ScopedConstraintHandler(mapper, outputSignal, DEFAULT_PRIORITY);
        });
    }
}
