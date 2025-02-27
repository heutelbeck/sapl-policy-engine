/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.AttributeFactory;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a head attribute finder step to a previous
 * value.
 */
public class HeadAttributeFinderStepImplCustom extends HeadAttributeFinderStepImpl {

    private static final String ATTRIBUTE_FINDER_STEP_NOT_PERMITTED_ERROR = "AttributeFinderStep not permitted in filter selection steps.";

    @Override
    public Flux<Val> apply(@NonNull Val entity) {
        return Flux.from(AttributeFactory.evaluateAttibute(this, identifier, entity, arguments).next());
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val entity, int stepId, @NonNull FilterStatement statement) {
        return Flux.just(ErrorFactory.error(this, ATTRIBUTE_FINDER_STEP_NOT_PERMITTED_ERROR));
    }

}
