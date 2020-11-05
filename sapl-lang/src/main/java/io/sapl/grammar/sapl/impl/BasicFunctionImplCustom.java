/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of functions.
 *
 * Grammar: {BasicFunction} fsteps+=ID ('.' fsteps+=ID)* arguments=Arguments
 * steps+=Step*; {Arguments} '(' (args+=Expression (',' args+=Expression)*)?
 * ')';
 */
public class BasicFunctionImplCustom extends BasicFunctionImpl {

	@Override
	public Flux<Val> evaluate(@NonNull EvaluationContext ctx, @NonNull Val relativeNode) {
		if (getArguments() != null && !getArguments().getArgs().isEmpty()) {
			// create a list of Fluxes containing the results of evaluating the
			// individual argument expressions.
			final List<Flux<Val>> arguments = new ArrayList<>(getArguments().getArgs().size());
			for (Expression argument : getArguments().getArgs()) {
				arguments.add(argument.evaluate(ctx, relativeNode));
			}
			// evaluate the function for each value assignment of the arguments
			return Flux.combineLatest(arguments, Function.identity()).switchMap(
					parameters -> evaluateFunction(Arrays.copyOf(parameters, parameters.length, Val[].class), ctx))
					.flatMap(funResult -> evaluateStepsFilterSubtemplate(funResult, getSteps(), ctx, relativeNode));
		} else {
			// No need to evaluate arguments. Just evaluate and apply steps.
			return evaluateFunction(null, ctx)
					.flatMap(funResult -> evaluateStepsFilterSubtemplate(funResult, getSteps(), ctx, relativeNode));
		}
	}

	private Flux<Val> evaluateFunction(Val[] parameters, EvaluationContext ctx) {
		try {
			return Flux.just(ctx.getFunctionCtx().evaluate(functionName(ctx), parameters));
		} catch (FunctionException e) {
			return Flux.error(new PolicyEvaluationException(e));
		}
	}

	private String functionName(EvaluationContext ctx) {
		String joinedSteps = String.join(".", getFsteps());
		return ctx.getImports().getOrDefault(joinedSteps, joinedSteps);
	}

}
