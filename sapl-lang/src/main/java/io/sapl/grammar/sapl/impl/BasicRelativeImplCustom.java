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
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

/**
 * Implements the evaluation of relative expressions.
 * <p>
 * Grammar: {BasicRelative} '@' steps+=Step*;
 */
public class BasicRelativeImplCustom extends BasicRelativeImpl {

    private static final String NO_RELATIVE_NODE_ERROR = "Relative expression error. No relative node.";

    @Override
    public Flux<Val> evaluate() {
        return Flux.deferContextual(this::evaluateRelativeNode);
    }

    private Flux<Val> evaluateRelativeNode(ContextView ctx) {
        final var relativeNode = AuthorizationContext.getRelativeNode(ctx);

        if (relativeNode.isUndefined())
            return Flux.just(ErrorFactory.error(this, NO_RELATIVE_NODE_ERROR).withTrace(BasicRelative.class));

        return Flux.just(relativeNode.withTrace(BasicRelative.class, true, relativeNode))
                .switchMap(v -> resolveStepsFiltersAndSubTemplates(steps).apply(v));
    }

}
