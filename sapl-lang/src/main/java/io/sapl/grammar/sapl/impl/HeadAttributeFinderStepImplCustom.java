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

import static io.sapl.interpreter.context.AuthorizationContext.getImports;

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import io.sapl.grammar.sapl.impl.util.TargetExpressionUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a head attribute finder step to a previous
 * value.
 */
public class HeadAttributeFinderStepImplCustom extends HeadAttributeFinderStepImpl {

    private static final String ATTRIBUTE_FINDER_STEP_NOT_PERMITTED_ERROR = "AttributeFinderStep not permitted in filter selection steps.";
    private static final String UNDEFINED_VALUE_ERROR                     = "Undefined value handed over as parameter to policy information point";
    private static final String EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR        = "Attribute resolution error. Attributes not allowed in target.";

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {

        return Flux.deferContextual(ctxView -> {
            var attributeName = FunctionUtil.resolveAbsoluteFunctionName(getIdSteps(), getImports(ctxView));

            if (parentValue.isError()) {
                return Flux.just(parentValue.withTrace(HeadAttributeFinderStep.class,
                        Map.of(Trace.PARENT_VALUE, parentValue, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            if (TargetExpressionUtil.isInTargetExpression(this)) {
                return Flux.just(Val.error(EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR).withTrace(HeadAttributeFinderStep.class,
                        Map.of(Trace.PARENT_VALUE, parentValue, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            if (parentValue.isUndefined()) {
                return Flux.just(Val.error(UNDEFINED_VALUE_ERROR).withTrace(HeadAttributeFinderStep.class,
                        Map.of(Trace.PARENT_VALUE, parentValue, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            return AuthorizationContext.getAttributeContext(ctxView).evaluateAttribute(attributeName, parentValue,
                    getArguments(), AuthorizationContext.getVariables(ctxView)).take(1);
        });
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        return Val.errorFlux(ATTRIBUTE_FINDER_STEP_NOT_PERMITTED_ERROR);
    }

}
