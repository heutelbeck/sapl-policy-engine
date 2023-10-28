/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class FunctionUtil {

	public Flux<Val[]> combineArgumentFluxes(Arguments arguments) {
		if (arguments == null || arguments.getArgs().isEmpty())
			return Mono.just(new Val[0]).flux();

		return combine(argumentFluxes(arguments));
	}

	public String resolveAbsoluteFunctionName(Iterable<String> steps, Map<String, String> imports) {
		var functionName = mergeStepsToName(steps);
		return imports.getOrDefault(functionName, functionName);
	}

	public String resolveAbsoluteFunctionName(String unresolvedFunctionName, Map<String, String> imports) {
		return imports.getOrDefault(unresolvedFunctionName, unresolvedFunctionName);
	}

	public Mono<Val> evaluateFunctionMono(Iterable<String> fsteps, Val... parameters) {
		return evaluateFunctionMono(mergeStepsToName(fsteps), parameters);
	}

	public Mono<Val> evaluateFunctionMono(String unresolvedFunctionName, Val... parameters) {
		return Mono.deferContextual(ctx -> Mono.just(AuthorizationContext.functionContext(ctx).evaluate(
				resolveAbsoluteFunctionName(unresolvedFunctionName, AuthorizationContext.getImports(ctx)),
				parameters)));
	}

	public Mono<Val> evaluateFunctionWithLeftHandArgumentMono(Iterable<String> fsteps, Val leftHandArgument,
			Val... parameters) {
		Val[] mergedParameters = new Val[parameters.length + 1];
		mergedParameters[0] = leftHandArgument;
		System.arraycopy(parameters, 0, mergedParameters, 1, parameters.length);
		return evaluateFunctionMono(fsteps, mergedParameters);
	}

	private Stream<Flux<Val>> argumentFluxes(Arguments arguments) {
		return arguments.getArgs().stream().map(Expression::evaluate);
	}

	private Flux<Val[]> combine(Stream<Flux<Val>> argumentFluxes) {
		List<Flux<Val>> x = argumentFluxes.collect(Collectors.toList());
		return Flux.combineLatest(x, e -> Arrays.copyOf(e, e.length, Val[].class));
	}

	private String mergeStepsToName(Iterable<String> steps) {
		return String.join(".", steps);
	}

}
