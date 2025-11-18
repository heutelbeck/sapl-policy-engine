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

@UtilityClass
public class AttributeCompiler {

    private static final AttributeFinderOptions DEFAULT_OPTIONS = new AttributeFinderOptions("none", 3000L, 30000L,
            1000L, 3, false, null, null);

    private static final String UNDEFINED_VALUE_ERROR = "Undefined value handed over as left-hand parameter to policy information point";

    private static final String OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS = "attributeFinderOptions";
    private static final String OPTION_FIELD_BACKOFF                  = "backoffMs";
    private static final String OPTION_FIELD_FRESH                    = "fresh";
    private static final String OPTION_FIELD_INITIAL_TIMEOUT          = "initialTimeOutMs";
    private static final String OPTION_FIELD_POLL_INTERVAL            = "pollIntervalMs";
    private static final String OPTION_FIELD_RETRIES                  = "retries";

    private static final String VARIABLE_NAME_SAPL = "SAPL";

    public static CompiledExpression compileAttributeFinderStep(CompiledExpression entity,
            AttributeFinderStep attributeFinderStep, CompilationContext context) {
        return compileAttributeFinderStep(attributeFinderStep, entity, attributeFinderStep.getIdentifier(),
                attributeFinderStep.getArguments(), attributeFinderStep.getAttributeFinderOptions(), context);
    }

    public static CompiledExpression compileEnvironmentAttribute(BasicEnvironmentAttribute envAttribute,
            CompilationContext context) {
        return compileAttributeFinderStep(envAttribute, null, envAttribute.getIdentifier(), envAttribute.getArguments(),
                envAttribute.getAttributeFinderOptions(), context);

    }

    public static CompiledExpression compileHeadAttributeFinderStep(CompiledExpression entity,
            HeadAttributeFinderStep attributeFinderStep, CompilationContext context) {
        val fullStream = compileAttributeFinderStep(attributeFinderStep, entity, attributeFinderStep.getIdentifier(),
                attributeFinderStep.getArguments(), attributeFinderStep.getAttributeFinderOptions(), context);
        return fullStreamToHead(fullStream);
    }

    public static CompiledExpression compileHeadEnvironmentAttribute(BasicEnvironmentHeadAttribute envAttribute,
            CompilationContext context) {
        val fullStream = compileAttributeFinderStep(envAttribute, null, envAttribute.getIdentifier(),
                envAttribute.getArguments(), envAttribute.getAttributeFinderOptions(), context);
        return fullStreamToHead(fullStream);
    }

    private CompiledExpression fullStreamToHead(CompiledExpression fullStream) {
        return switch (fullStream) {
        case ErrorValue error                  -> error;
        case Value ignored                     ->
            throw new SaplCompilerException("Compilation error. Got Value from PIP. Indicates implementation bug.");
        case PureExpression ignored            -> throw new SaplCompilerException(
                "Compilation error. Got PureExpression from PIP. Indicates implementation bug.");
        case StreamExpression streamExpression -> new StreamExpression(streamExpression.stream().take(1));
        };
    }

    private static CompiledExpression compileAttributeFinderStep(EObject source, CompiledExpression entity,
            FunctionIdentifier identifier, Arguments stepArguments, Expression attributeFinderOptions,
            CompilationContext context) {
        if (entity instanceof ErrorValue) {
            return entity;
        }
        var compiledOptions = ExpressionCompiler.compileExpression(attributeFinderOptions, context);
        if (compiledOptions == null) {
            compiledOptions = Value.EMPTY_OBJECT;
        }

        val entityFlux           = entity == null ? Flux.just(Value.UNDEFINED)
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

    private static Flux<Value> evaluatedAttributeFinder(java.lang.Object[] evaluatedAttributeFinderParameters,
            String attributeName, boolean isEnvironmentAttribute) {
        if (evaluatedAttributeFinderParameters.length < 2) {
            return Flux.just(Value
                    .error("Internal PDP Error. Attribute evaluation must have at least two parameters, but got %d."
                            .formatted(evaluatedAttributeFinderParameters.length)));
        }
        val entity = (Value) evaluatedAttributeFinderParameters[0];

        if (entity instanceof ErrorValue) {
            return Flux.just(entity);
        }
        if (!isEnvironmentAttribute && entity instanceof UndefinedValue) {
            return Flux.just(Value.error(UNDEFINED_VALUE_ERROR));
        }
        val maybeOptions = (Options) evaluatedAttributeFinderParameters[1];
        if (maybeOptions instanceof OptionsError(ErrorValue error)) {
            return Flux.just(error);
        }

        val options = (AttributeFinderOptions) maybeOptions;
        if (options.attributeBroker == null) {
            return Flux.just(Value.error("Internal PDP Error. AttributeBroker not configured in evaluation context."));
        }

        var values     = Arrays.stream(evaluatedAttributeFinderParameters, 2, evaluatedAttributeFinderParameters.length)
                .map(obj -> (Value) obj).toList();
        val invocation = new AttributeFinderInvocation(options.configurationId, attributeName,
                isEnvironmentAttribute ? null : entity, values, options.variables, options.initialTimeOutDuration(),
                options.pollIntervalDuration(), options.backoffDuration(), options.retries, options.fresh);
        return options.attributeBroker.attributeStream(invocation);
    }

    /**
     * Retrieves global attribute finder defaults from the SAPL variable context.
     *
     * @param variables the current policy variables
     * @return an optional containing the global defaults object, or empty if not
     * present
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

    private Long extractLong(Value v) {
        if (v instanceof NumberValue number) {
            return number.value().longValue();
        }
        return null;
    }

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

    private sealed interface Options permits AttributeFinderOptions, OptionsError {
    }

    private record OptionsError(ErrorValue error) implements Options {}

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
