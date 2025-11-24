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
package io.sapl.compiler;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.api.model.Value;
import io.sapl.grammar.sapl.*;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Compiles SAPL attribute finder expressions into reactive streams that invoke
 * PIPs.
 * <p>
 * Options precedence: inline > {@code SAPL.attributeFinderOptions} variable >
 * defaults.
 * <p>
 * Uses {@code switchMap} - parameter changes cancel and restart PIP
 * invocations. Type mismatches in options silently
 * fall through to next precedence level. UNDEFINED entities rejected for
 * attribute steps, allowed for environment
 * attributes. {@link AttributeBroker} must be present in reactor context
 * (runtime requirement).
 */
@Slf4j
@UtilityClass
public class AttributeCompiler {

    /** Defaults: 3s timeout, 30s poll, 1s backoff, 3 retries, caching allowed. */
    private static final AttributeFinderOptions DEFAULT_OPTIONS = new AttributeFinderOptions("none", 3000L, 30000L,
            1000L, 3, false, null, null);

    private static final String ERROR_ATTRIBUTE_BROKER_NOT_CONFIGURED   = "Internal PDP Error. AttributeBroker not configured in evaluation context.";
    private static final String ERROR_ATTRIBUTE_PARAMETERS_INSUFFICIENT = "Internal PDP Error. Attribute evaluation must have at least two parameters, but got %d.";
    private static final String ERROR_PIP_RETURNED_PURE_EXPRESSION      = "Compilation error. Got PureExpression from PIP. Indicates implementation bug.";
    private static final String ERROR_PIP_RETURNED_VALUE                = "Compilation error. Got Value from PIP. Indicates implementation bug.";
    private static final String ERROR_UNDEFINED_VALUE_LEFT_HAND         = "Undefined value handed over as left-hand parameter to policy information point";

    private static final String OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS = "attributeFinderOptions";
    private static final String OPTION_FIELD_BACKOFF                  = "backoffMs";
    private static final String OPTION_FIELD_FRESH                    = "fresh";
    private static final String OPTION_FIELD_INITIAL_TIMEOUT          = "initialTimeOutMs";
    private static final String OPTION_FIELD_POLL_INTERVAL            = "pollIntervalMs";
    private static final String OPTION_FIELD_RETRIES                  = "retries";
    private static final String VARIABLE_NAME_SAPL                    = "SAPL";

    /**
     * Compiles attribute finder step (e.g., {@code entity.<pip.attr>}). Entity
     * becomes first parameter. UNDEFINED
     * entity rejected at runtime.
     */
    public static CompiledExpression compileAttributeFinderStep(CompiledExpression entity,
            AttributeFinderStep attributeFinderStep, CompilationContext context) {
        return compileAttributeFinderStep(attributeFinderStep, entity, attributeFinderStep.getIdentifier(),
                attributeFinderStep.getArguments(), attributeFinderStep.getAttributeFinderOptions(), context);
    }

    /**
     * Compiles environment attribute (e.g., {@code <time.now>}). Entity set to
     * UNDEFINED, which is allowed for
     * environment attributes.
     */
    public static CompiledExpression compileEnvironmentAttribute(BasicEnvironmentAttribute envAttribute,
            CompilationContext context) {
        log.debug("Compiling environment attribute: '{}'", envAttribute.getIdentifier());
        return compileAttributeFinderStep(envAttribute, null, envAttribute.getIdentifier(), envAttribute.getArguments(),
                envAttribute.getAttributeFinderOptions(), context);

    }

    /**
     * Compiles head attribute finder step (e.g., {@code entity.<pip.attr>|}).
     * Applies {@code take(1)} - upstream may
     * not cancel immediately.
     */
    public static CompiledExpression compileHeadAttributeFinderStep(CompiledExpression entity,
            HeadAttributeFinderStep attributeFinderStep, CompilationContext context) {
        val fullStream = compileAttributeFinderStep(attributeFinderStep, entity, attributeFinderStep.getIdentifier(),
                attributeFinderStep.getArguments(), attributeFinderStep.getAttributeFinderOptions(), context);
        return fullStreamToHead(fullStream);
    }

    /**
     * Compiles head environment attribute (e.g., {@code <time.now>|}).
     */
    public static CompiledExpression compileHeadEnvironmentAttribute(BasicEnvironmentHeadAttribute envAttribute,
            CompilationContext context) {
        val fullStream = compileAttributeFinderStep(envAttribute, null, envAttribute.getIdentifier(),
                envAttribute.getArguments(), envAttribute.getAttributeFinderOptions(), context);
        return fullStreamToHead(fullStream);
    }

    /**
     * Applies {@code take(1)} to stream. Throws if not {@link StreamExpression}
     * (implementation bug).
     */
    private CompiledExpression fullStreamToHead(CompiledExpression fullStream) {
        return switch (fullStream) {
        case ErrorValue error                     -> error;
        case Value ignored                        -> throw new SaplCompilerException(ERROR_PIP_RETURNED_VALUE);
        case PureExpression ignored               ->
            throw new SaplCompilerException(ERROR_PIP_RETURNED_PURE_EXPRESSION);
        case StreamExpression(Flux<Value> stream) -> new StreamExpression(stream.take(1));
        };
    }

    /**
     * Core method. Uses {@code combineLatest} + {@code switchMap}. Null
     * options/arguments replaced with empty. Options
     * deferred via {@code deferContextual}. Parameter array: [entity, options,
     * args...].
     */
    private static CompiledExpression compileAttributeFinderStep(EObject source, CompiledExpression entity,
            FunctionIdentifier identifier, Arguments stepArguments, Expression attributeFinderOptions,
            CompilationContext context) {
        log.debug("Compiling attribute finder step for: '{}' with entity {}", identifier, entity);
        if (entity instanceof ErrorValue) {
            return entity;
        }
        var compiledOptions = ExpressionCompiler.compileExpression(attributeFinderOptions, context);
        if (compiledOptions == null) {
            compiledOptions = Value.EMPTY_OBJECT;
        }
        if (entity instanceof UndefinedValue) {
            throw new SaplCompilerException(ERROR_UNDEFINED_VALUE_LEFT_HAND);
        }
        val entityFlux           = entity == null ? Flux.just(Value.NULL)
                : ExpressionCompiler.compiledExpressionToFlux(entity);
        val optionsParameterFlux = ExpressionCompiler.compiledExpressionToFlux(compiledOptions);
        val effectiveOptionsFlux = Flux.deferContextual(
                ctx -> optionsParameterFlux.map(optionsParameterValue -> calculateOptions(ctx, optionsParameterValue)));

        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (stepArguments != null && stepArguments.getArgs() != null) {
            arguments = ExpressionCompiler.compileArguments(stepArguments.getArgs(), context);
        }

        val attributeFinderParameterSources = new ArrayList<Flux<?>>(arguments.arguments().length + 2);
        attributeFinderParameterSources.add(entityFlux);
        attributeFinderParameterSources.add(effectiveOptionsFlux);
        for (val argument : arguments.arguments()) {
            attributeFinderParameterSources.add(ExpressionCompiler.compiledExpressionToFlux(argument));
        }

        val resolvedAttributeName = ImportResolver.resolveFunctionIdentifierByImports(source, identifier);

        val attributeStream = Flux.combineLatest(attributeFinderParameterSources, Function.identity())
                .switchMap(combined -> evaluatedAttributeFinder(combined, resolvedAttributeName, entity == null));
        return new StreamExpression(attributeStream);
    }

    /**
     * Validates params, delegates to AttributeBroker. Parameters: [entity, options,
     * args...]. Environment attributes
     * pass entity as null (not UNDEFINED). Unchecked casts - ClassCastException if
     * compilation bug.
     */
    private static Flux<Value> evaluatedAttributeFinder(java.lang.Object[] evaluatedAttributeFinderParameters,
            String attributeName, boolean isEnvironmentAttribute) {
        if (evaluatedAttributeFinderParameters.length < 2) {
            return Flux.just(Value.error(
                    String.format(ERROR_ATTRIBUTE_PARAMETERS_INSUFFICIENT, evaluatedAttributeFinderParameters.length)));
        }
        val entity = (Value) evaluatedAttributeFinderParameters[0];

        if (entity instanceof ErrorValue) {
            return Flux.just(entity);
        }
        if (!isEnvironmentAttribute && entity instanceof UndefinedValue) {
            return Flux.just(Value.error(ERROR_UNDEFINED_VALUE_LEFT_HAND));
        }
        val maybeOptions = (Options) evaluatedAttributeFinderParameters[1];
        if (maybeOptions instanceof OptionsError(ErrorValue error)) {
            return Flux.just(error);
        }

        val options = (AttributeFinderOptions) maybeOptions;
        if (options.attributeBroker == null) {
            return Flux.just(Value.error(ERROR_ATTRIBUTE_BROKER_NOT_CONFIGURED));
        }

        var values     = Arrays.stream(evaluatedAttributeFinderParameters, 2, evaluatedAttributeFinderParameters.length)
                .map(Value.class::cast).toList();
        val invocation = new AttributeFinderInvocation(options.configurationId, attributeName,
                isEnvironmentAttribute ? null : entity, values, options.variables, options.initialTimeOutDuration(),
                options.pollIntervalDuration(), options.backoffDuration(), options.retries, options.fresh);
        return options.attributeBroker.attributeStream(invocation);
    }

    /**
     * Extracts {@code SAPL.attributeFinderOptions} from variables. Returns empty if
     * missing or wrong type.
     */
    private static Optional<ObjectValue> globalDefaults(Map<String, Value> variables) {
        val saplOptions = variables.get(VARIABLE_NAME_SAPL);
        if (!(saplOptions instanceof ObjectValue saplOptionsObject)) {
            return Optional.empty();
        }
        val attributeFinderOptions = saplOptionsObject.get(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS);
        if (!(attributeFinderOptions instanceof ObjectValue attributeFinderOptionsObject)) {
            return Optional.empty();
        }
        return Optional.of(attributeFinderOptionsObject);
    }

    /**
     * Merges options at subscription time. Config ID from context, not options.
     */
    private static Options calculateOptions(ContextView ctx, Value options) {
        if (options instanceof ErrorValue error) {
            return new OptionsError(error);
        }
        val evaluationContext = ctx.get(EvaluationContext.class);
        val variables         = evaluationContext.variables();
        return new AttributeFinderOptions(
                evaluationContext.configurationId() == null ? DEFAULT_OPTIONS.configurationId
                        : evaluationContext.configurationId(),
                optionValue(OPTION_FIELD_INITIAL_TIMEOUT, variables, options, DEFAULT_OPTIONS.initialTimeOutMs,
                        AttributeCompiler::extractLong),
                optionValue(OPTION_FIELD_POLL_INTERVAL, variables, options, DEFAULT_OPTIONS.pollIntervalMs,
                        AttributeCompiler::extractLong),
                optionValue(OPTION_FIELD_BACKOFF, variables, options, DEFAULT_OPTIONS.backoffMs,
                        AttributeCompiler::extractLong),
                optionValue(OPTION_FIELD_RETRIES, variables, options, DEFAULT_OPTIONS.retries,
                        AttributeCompiler::extractInteger),
                optionValue(OPTION_FIELD_FRESH, variables, options, DEFAULT_OPTIONS.fresh,
                        AttributeCompiler::extractBoolean),
                evaluationContext.variables(), evaluationContext.attributeBroker());
    }

    /** BigDecimal to long - truncates fractional, may overflow. */
    private Long extractLong(Value v) {
        if (v instanceof NumberValue number) {
            return number.value().longValue();
        }
        return null;
    }

    /** BigDecimal to int - truncates fractional, may overflow. */
    private Integer extractInteger(Value v) {
        if (v instanceof NumberValue number) {
            return number.value().intValue();
        }
        return null;
    }

    private Boolean extractBoolean(Value v) {
        if (v instanceof BooleanValue bool) {
            return bool.value();
        }
        return null;
    }

    /**
     * Precedence: inline > global > default. Type mismatches silently fall through
     * to next level.
     */
    private static <T> @NonNull T optionValue(String fieldName, Map<String, Value> variables, Value options,
            @NonNull T defaultValue, Function<Value, T> extractor) {
        if (options instanceof ObjectValue optionsObject) {
            val node = optionsObject.get(fieldName);
            if (node != null) {
                val value = extractor.apply(node);
                if (value != null)
                    return value;
            }
        }

        val maybeGlobal = globalDefaults(variables);
        if (maybeGlobal.isPresent()) {
            val globalNode = maybeGlobal.get().get(fieldName);
            if (globalNode != null) {
                val value = extractor.apply(globalNode);
                if (value != null)
                    return value;
            }
        }

        return defaultValue;
    }

    /**
     * Result of option calculation - either {@link AttributeFinderOptions} or
     * {@link OptionsError}.
     */
    private sealed interface Options permits AttributeFinderOptions, OptionsError {
    }

    /** Wraps compilation errors from inline options. */
    private record OptionsError(ErrorValue error) implements Options {}

    /**
     * Resolved PIP invocation config. {@code configurationId} from
     * EvaluationContext. {@code backoffMs} is exponential
     * base. {@code retries} is additional attempts (3 = 4 total).
     */
    private record AttributeFinderOptions(
            String configurationId,
            long initialTimeOutMs,
            long pollIntervalMs,
            long backoffMs,
            int retries,
            boolean fresh,
            Map<String, Value> variables,
            AttributeBroker attributeBroker) implements Options {

        public @NonNull Duration backoffDuration() {
            return Duration.ofMillis(backoffMs);
        }

        public @NonNull Duration initialTimeOutDuration() {
            return Duration.ofMillis(initialTimeOutMs);
        }

        public @NonNull Duration pollIntervalDuration() {
            return Duration.ofMillis(pollIntervalMs);
        }
    }

}
