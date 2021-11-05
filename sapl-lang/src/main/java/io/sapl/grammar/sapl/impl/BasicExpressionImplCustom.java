/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Superclass of basic expressions providing a method to evaluate the steps, filter and
 * subtemplate possibly being part of the basic expression.
 *
 * Grammar:
 * {@code BasicExpression returns Expression: Basic (FILTER filter=FilterComponent |
 * SUBTEMPLATE subtemplate=BasicExpression)? ;
 *
 * Basic returns BasicExpression: {BasicGroup} '(' expression=Expression ')' steps+=Step*
 * | {BasicValue} value=Value steps+=Step* | {BasicFunction} fsteps+=ID ('.' fsteps+=ID)*
 * arguments=Arguments steps+=Step* | {BasicIdentifier} identifier=ID steps+=Step* |
 * {BasicRelative} '@' steps+=Step* ;}
 */
public class BasicExpressionImplCustom extends BasicExpressionImpl {

	protected Function<? super Val, Publisher<? extends Val>> resolveStepsFiltersAndSubtemplates(EList<Step> steps,
			EvaluationContext ctx, Val relativeNode) {
		return resolveSteps(steps, 0, ctx, relativeNode);
	}

	private Function<? super Val, Publisher<? extends Val>> resolveSteps(EList<Step> steps, int stepId,
			EvaluationContext ctx, Val relativeNode) {
		if (steps == null || stepId == steps.size()) {
			return value -> resolveFilterOrSubtemplate(value, ctx);
		}
		return value -> steps.get(stepId).apply(value, ctx, relativeNode)
				.switchMap(resolveSteps(steps, stepId + 1, ctx, relativeNode));
	}

	private Flux<Val> resolveFilterOrSubtemplate(Val value, EvaluationContext ctx) {
		if (filter != null) {
			return filter.apply(value, ctx, value);
		}
		if (subtemplate != null) {
			return applySubTemplate(ctx, value);
		}
		return Flux.just(value);
	}

	private Flux<Val> applySubTemplate(EvaluationContext ctx, Val value) {
		if (!value.isArray()) {
			return subtemplate.evaluate(ctx, value);
		}
		var array = value.getArrayNode();
		if (array.isEmpty()) {
			return Flux.just(Val.ofEmptyArray());
		}
		var itemFluxes = new ArrayList<Flux<Val>>(array.size());
		for (var element : array) {
			itemFluxes.add(subtemplate.evaluate(ctx, Val.of(element)));
		}
		return Flux.combineLatest(itemFluxes, RepackageUtil::recombineArray);
	}

}
