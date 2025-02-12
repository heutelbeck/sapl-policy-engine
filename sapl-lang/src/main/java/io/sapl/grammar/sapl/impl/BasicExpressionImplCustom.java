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

import java.util.ArrayList;
import java.util.function.Function;

import org.eclipse.emf.common.util.EList;
import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicExpression;
import io.sapl.grammar.sapl.Step;
import io.sapl.grammar.sapl.impl.util.RepackageUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;

/**
 * Superclass of basic expressions providing a method to evaluate the steps,
 * filter and sub template possibly being part of the basic expression.
 * <p>
 * Grammar:
 * {@code BasicExpression returns Expression: Basic (FILTER filter=FilterComponent |
 * SUBTEMPLATE subtemplate=BasicExpression)? ;
 *
<p>
 * Basic returns BasicExpression: {BasicGroup} '(' expression=Expression ')'
 * steps+=Step* | {BasicValue} value=Value steps+=Step* | {BasicFunction}
 * fsteps+=ID ('.' fsteps+=ID)* arguments=Arguments steps+=Step* |
 * {BasicIdentifier} identifier=ID steps+=Step* | {BasicRelative} '@'
 * steps+=Step* ;}
 */
public class BasicExpressionImplCustom extends BasicExpressionImpl {

    protected Function<Val, Publisher<Val>> resolveStepsFiltersAndSubTemplates(EList<Step> steps) {
        return resolveSteps(steps, 0);
    }

    private Function<Val, Publisher<Val>> resolveSteps(EList<Step> steps, int stepId) {
        if (steps == null || stepId == steps.size()) {
            return this::resolveFilterOrSubTemplate;
        }
        return value -> steps.get(stepId).apply(value).switchMap(v -> resolveSteps(steps, stepId + 1).apply(v));
    }

    private Flux<Val> resolveFilterOrSubTemplate(Val value) {
        if (filter != null) {
            return filter.apply(value).contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx,
                    value.withTrace(BasicExpression.class, true, value)));
        }
        if (subtemplate != null) {
            return applySubTemplate(value);
        }
        return Flux.just(value);
    }

    private Flux<Val> applySubTemplate(Val value) {
        if (!value.isArray()) {
            return subtemplate.evaluate().contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx,
                    value.withTrace(BasicExpression.class, true, value)));
        }
        final var array = value.getArrayNode();
        if (array.isEmpty()) {
            return Flux.just(value.withTrace(BasicExpression.class, true, value));
        }
        final var itemFluxes = new ArrayList<Flux<Val>>(array.size());
        for (var element : array) {
            itemFluxes.add(subtemplate.evaluate().contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx,
                    Val.of(element).withTrace(BasicExpression.class, true, value))));
        }
        return Flux.combineLatest(itemFluxes, RepackageUtil::recombineArray);
    }

}
