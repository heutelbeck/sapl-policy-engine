/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of identifiers.
 * <p>
 * Grammar: {BasicIdentifier} identifier=ID steps+=Step*;
 */
public class BasicIdentifierImplCustom extends BasicIdentifierImpl {

    @Override
    public Flux<Val> evaluate() {
        return Flux.deferContextual(ctx -> {
            var identifierFlux = Flux.just(AuthorizationContext.getVariable(ctx, getIdentifier()));
            return identifierFlux.switchMap(v -> resolveStepsFiltersAndSubTemplates(steps).apply(v))
                    .map(val -> val.withTrace(BasicIdentifier.class, true,
                            Map.of(Trace.IDENTIFIER, Val.of(getIdentifier()), Trace.VALUE, val)));
        });
    }

}
