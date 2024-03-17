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

import static io.sapl.interpreter.context.AuthorizationContext.getAttributeContext;
import static io.sapl.interpreter.context.AuthorizationContext.getImports;
import static io.sapl.interpreter.context.AuthorizationContext.getVariables;

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import io.sapl.grammar.sapl.impl.util.TargetExpressionUtil;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of an attribute finder step to a previous value.
 * <p>
 * Grammar: Step: &#39;.&#39; ({AttributeFinderStep} &#39;&lt;&#39; idSteps+=ID
 * (&#39;.&#39; idSteps+=ID)* &#39;&gt;&#39;) ;
 */
public class AttributeFinderStepImplCustom extends AttributeFinderStepImpl {

    private static final String ATTRIBUTE_FINDER_STEP_NOT_PERMITTED_ERROR = "AttributeFinderStep not permitted in filter selection steps.";
    private static final String UNDEFINED_VALUE_ERROR                     = "Undefined value handed over as left-hand parameter to policy information point";
    private static final String EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR        = "Attribute resolution error. Attributes are not allowed in target.";

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {

        return Flux.deferContextual(ctxView -> {
            var attributeName = FunctionUtil.resolveAbsoluteFunctionName(getIdSteps(), getImports(ctxView));

            if (parentValue.isError()) {
                return Flux.just(parentValue.withTrace(AttributeFinderStep.class, false,
                        Map.of(Trace.PARENT_VALUE, parentValue, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            if (TargetExpressionUtil.isInTargetExpression(this)) {
                return Flux.just(
                        Val.error(this, EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR).withTrace(AttributeFinderStep.class, false,
                                Map.of(Trace.PARENT_VALUE, parentValue, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            if (parentValue.isUndefined()) {
                return Flux.just(Val.error(this, UNDEFINED_VALUE_ERROR).withTrace(AttributeFinderStep.class, false,
                        Map.of(Trace.PARENT_VALUE, parentValue, Trace.ATTRIBUTE, Val.of(attributeName))));
            }

            var attributeContext = getAttributeContext(ctxView);
            var variables        = getVariables(ctxView);
            // @formatter:off
			return attributeContext
					.evaluateAttribute(this, attributeName, parentValue, getArguments(), variables)
					.distinctUntilChanged();
			// @formatter:on
        });
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        return Flux.just(Val.error(this, ATTRIBUTE_FINDER_STEP_NOT_PERMITTED_ERROR));
    }

}
