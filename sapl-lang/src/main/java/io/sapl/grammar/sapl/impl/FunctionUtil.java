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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class FunctionUtil {

	public Flux<Val[]> combineArgumentFluxes(Arguments arguments, EvaluationContext ctx, Val relativeNode) {
		if (arguments == null || arguments.getArgs().size() == 0) {
			return Mono.just(new Val[0]).flux();
		}
		return combine(argumentFluxes(arguments, ctx, relativeNode));
	}

	public String resolveAbsoluteFunctionName(Iterable<String> steps, EvaluationContext ctx) {
		var functionName = mergeStepsToName(steps);
		return ctx.getImports().getOrDefault(functionName, functionName);
	}

	public Mono<Val> evaluateFunctionMono(Iterable<String> fsteps, EvaluationContext ctx, Val... parameters) {
		return evaluateFunctionMono(mergeStepsToName(fsteps), ctx, parameters);
	}

	public Mono<Val> evaluateFunctionMono(String functionName, EvaluationContext ctx, Val... parameters) {
		return Mono.just(evaluateFunction(functionName, ctx, parameters));
	}

	public Mono<Val> evaluateFunctionWithLeftHandArgumentMono(String functionName, EvaluationContext ctx,
			Val leftHandArgument, Val... parameters) {
		Val[] mergedParameters = new Val[parameters.length + 1];
		mergedParameters[0] = leftHandArgument;
		for (int i = 0; i < parameters.length; i++) {
			mergedParameters[i + 1] = parameters[i];
		}
		return evaluateFunctionMono(functionName, ctx, mergedParameters);
	}

	private Val evaluateFunction(String functionName, EvaluationContext ctx, Val... parameters) {
		return ctx.getFunctionCtx().evaluate(resolveAbsoluteFunctionName(functionName, ctx), parameters);
	}

	private Stream<Flux<Val>> argumentFluxes(Arguments arguments, EvaluationContext ctx, Val relativeNode) {
		return arguments.getArgs().stream().map(expression -> (Flux<Val>) expression.evaluate(ctx, relativeNode));
	}

	private Flux<Val[]> combine(Stream<Flux<Val>> argumentFluxes) {
		List<Flux<Val>> x = argumentFluxes.collect(Collectors.toList());
		return Flux.combineLatest(x, e -> Arrays.copyOf(e, e.length, Val[].class));
	}

	private String mergeStepsToName(Iterable<String> steps) {
		return String.join(".", steps);
	}

	private String resolveAbsoluteFunctionName(String functionName, EvaluationContext ctx) {
		return ctx.getImports().getOrDefault(functionName, functionName);
	}

}
