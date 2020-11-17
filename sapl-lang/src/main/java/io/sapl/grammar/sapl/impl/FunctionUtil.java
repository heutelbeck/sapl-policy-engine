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
		try {
			return ctx.getFunctionCtx().evaluate(resolveAbsoluteFunctionName(functionName, ctx), parameters);
		} catch (RuntimeException e) {
			var params = new StringBuilder();
			for (var i = 0; i < parameters.length; i++) {
				params.append(parameters[i]);
				if (i < parameters.length - 2)
					params.append(',');
			}
			return Val.error("Error during evaluation of function %s(%s): %s", functionName, params.toString(),
					e.getMessage());
		}
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
