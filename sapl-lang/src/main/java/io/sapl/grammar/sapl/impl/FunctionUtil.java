package io.sapl.grammar.sapl.impl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class FunctionUtil {

	public Flux<Val[]> combineArgumentFluxes(Arguments arguments, EvaluationContext ctx, Val relativeNode) {
		if (arguments == null || arguments.getArgs() == null || arguments.getArgs().size() == 0) {
			return Mono.just(new Val[0]).flux();
		}
		return combine(argumentFluxes(arguments, ctx, relativeNode));
	}

	public Flux<Val[]> combineLeftHandInputAndArgumentFluxes(Val parentNode, Arguments arguments, EvaluationContext ctx,
			Val relativeNode) {
		Stream<Flux<Val>> argumentFluxes = Stream.concat(Stream.of(Flux.just(parentNode)),
				argumentFluxes(arguments, ctx, relativeNode));
		return combine(argumentFluxes);
	}

	private Stream<Flux<Val>> argumentFluxes(@NonNull Arguments arguments, EvaluationContext ctx, Val relativeNode) {
		return arguments.getArgs().stream().map(expression -> (Flux<Val>) expression.evaluate(ctx, relativeNode));
	}

	private Flux<Val[]> combine(Stream<Flux<Val>> argumentFluxes) {
		List<Flux<Val>> x = argumentFluxes.collect(Collectors.toList());
		return Flux.combineLatest(x, e -> Arrays.copyOf(e, e.length, Val[].class));
	}

	public Val evaluateFunction(String functionName, EvaluationContext ctx, Val... parameters) {
		try {
			return ctx.getFunctionCtx().evaluate(resolveAbsoluteFunctionName(functionName, ctx), parameters);
		} catch (RuntimeException e) {
			return Val.error(e);
		}
	}

	public String mergeStepsToName(Iterable<String> steps) {
		return String.join(".", steps);
	}

	public Mono<Val> evaluateFunctionMono(Iterable<String> fsteps, EvaluationContext ctx, Val... parameters) {
		return evaluateFunctionMono(mergeStepsToName(fsteps), ctx, parameters);
	}

	public Mono<Val> evaluateFunctionMono(String functionName, EvaluationContext ctx, Val... parameters) {
		try {
			return Mono.just(evaluateFunction(functionName, ctx, parameters));
		} catch (RuntimeException e) {
			return Mono.just(Val.error(e));
		}
	}

	public Mono<Val> evaluateFunctionWithLeftHandArgumentMono(Iterable<String> fsteps, EvaluationContext ctx,
			Val leftHandArgument, Val... parameters) {
		return evaluateFunctionWithLeftHandArgumentMono(mergeStepsToName(fsteps), ctx, leftHandArgument, parameters);
	}

	public Mono<Val> evaluateFunctionWithLeftHandArgumentMono(String functionName, EvaluationContext ctx,
			Val leftHandArgument, Val... parameters) {
		try {
			return Mono.just(evaluateFunctionWithLeftHandArgument(functionName, ctx, leftHandArgument, parameters));
		} catch (RuntimeException e) {
			return Mono.just(Val.error(e));
		}
	}

	public Val evaluateFunctionWithLeftHandArgument(String functionName, EvaluationContext ctx, Val leftHandArgument,
			Val... parameters) {
		Val[] mergedParameters = new Val[parameters.length + 1];
		mergedParameters[0] = leftHandArgument;
		for (int i = 0; i < parameters.length; i++) {
			mergedParameters[i + 1] = parameters[i];
		}
		try {
			return ctx.getFunctionCtx().evaluate(resolveAbsoluteFunctionName(functionName, ctx), mergedParameters);
		} catch (RuntimeException e) {
			return Val.error(e);
		}
	}

	private String resolveAbsoluteFunctionName(String functionName, EvaluationContext ctx) {
		return ctx.getImports().getOrDefault(functionName, functionName);
	}

	public String resolveAbsoluteFunctionName(Iterable<String> steps, EvaluationContext ctx) {
		var functionName = mergeStepsToName(steps);
		return ctx.getImports().getOrDefault(functionName, functionName);
	}
}
