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
import java.util.Set;

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
 * Translates a {@code filterJsonContent} constraint into a {@link Mapper}
 * attached to the PEP's {@link Signal.OutputSignal}. Delegates to
 * {@link ContentFilter#getHandler(Value, ObjectMapper)}, which applies the
 * constraint's redact/blacken/replace actions to the payload via JSON
 * round-tripping. Returns an empty list when no {@link Signal.OutputSignal}
 * is in {@code supportedSignals}.
 */
@RequiredArgsConstructor
public class ContentFilteringProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE = "filterJsonContent";

    private static final int DEFAULT_PRIORITY = 30;

    private final ObjectMapper objectMapper;

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        var signalOpt = ConstraintHandlerProvider.constraintTypeAndAnyOutputSignal(constraint, CONSTRAINT_TYPE,
                supportedSignals);
        if (signalOpt.isEmpty()) {
            return List.of();
        }
        val mapper = ContentFilter.getHandler(constraint, objectMapper);
        return List.of(new ScopedConstraintHandler(mapper, signalOpt.get(), DEFAULT_PRIORITY));
    }
}
