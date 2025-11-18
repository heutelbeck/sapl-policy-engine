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
import io.sapl.grammar.sapl.AttributeFinderStep;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
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

    private static final String EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR = "Attribute resolution error. Attributes not allowed in target.";
    private static final String UNDEFINED_VALUE_ERROR              = "Undefined value handed over as left-hand parameter to policy information point";

    private static final String OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS = "attributeFinderOptions";
    private static final String OPTION_FIELD_BACKOFF                  = "backoffMs";
    private static final String OPTION_FIELD_FRESH                    = "fresh";
    private static final String OPTION_FIELD_INITIAL_TIMEOUT          = "initialTimeOutMs";
    private static final String OPTION_FIELD_POLL_INTERVAL            = "pollIntervalMs";
    private static final String OPTION_FIELD_RETRIES                  = "retries";

    private static final String VARIABLE_NAME_SAPL = "SAPL";

    private static final Value UNIMPLEMENTED = new ErrorValue("Unimplemented");

    public static CompiledExpression compileAttributeFinderStep(CompiledExpression entity,
            AttributeFinderStep attributeFinderStep, CompilationContext context) {
        if (entity instanceof ErrorValue) {
            return entity;
        }
        var compiledOptions = ExpressionCompiler.compileExpression(attributeFinderStep.getAttributeFinderOptions(),
                context);
        if (compiledOptions == null) {
            compiledOptions = Value.EMPTY_OBJECT;
        }

        val entityFlux           = ExpressionCompiler.compiledExpressionToFlux(entity);
        val optionsParameterFlux = ExpressionCompiler.compiledExpressionToFlux(compiledOptions);
        val effectiveOptionsFlux = Flux.deferContextual(
                ctx -> optionsParameterFlux.map(optionsParameterVale -> calculateOptions(ctx, optionsParameterVale)));

        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (attributeFinderStep.getArguments() != null && attributeFinderStep.getArguments().getArgs() != null) {
            arguments = ExpressionCompiler.compileArguments(attributeFinderStep.getArguments().getArgs(), context);
        }

        val attributeFinderParameterSources = new ArrayList<Flux<?>>(arguments.arguments().length + 2);
        attributeFinderParameterSources.add(entityFlux);
        attributeFinderParameterSources.add(effectiveOptionsFlux);
        for (val argument : arguments.arguments()) {
            attributeFinderParameterSources.add(ExpressionCompiler.compiledExpressionToFlux(argument));
        }

        val resolvedAttributeName = ImportResolver.resolveFunctionIdentifierByImports(attributeFinderStep,
                attributeFinderStep.getIdentifier());

        val attributeStream = Flux.combineLatest(attributeFinderParameterSources, Function.identity())
                .switchMap(combined -> evaluatedAttributeFinder(combined, resolvedAttributeName));
        return new StreamExpression(attributeStream);
    }

    private static Flux<Value> evaluatedAttributeFinder(java.lang.Object[] evaluatedAttributeFinderParameters,
            String attributeName) {
        if (evaluatedAttributeFinderParameters.length < 2) {
            return Flux.just(Value
                    .error("Internal PDP Error. Attribute evaluation must have at least two parameters, but got %d."
                            .formatted(evaluatedAttributeFinderParameters.length)));
        }
        val entity = (Value) evaluatedAttributeFinderParameters[0];

        if (entity instanceof ErrorValue) {
            return Flux.just(entity);
        }
        if (entity instanceof UndefinedValue) {
            return Flux.just(Value.error(UNDEFINED_VALUE_ERROR));
        }
        val maybeOptions = (Options) evaluatedAttributeFinderParameters[1];
        if (maybeOptions instanceof OptionsError(ErrorValue error)) {
            return Flux.just(error);
        }

        val options    = (AttributeFinderOptions) maybeOptions;
        var values     = new ArrayList<>(
                Arrays.stream(evaluatedAttributeFinderParameters, 2, evaluatedAttributeFinderParameters.length)
                        .map(obj -> (Value) obj).toList());
        val invocation = new AttributeFinderInvocation(options.configurationId, attributeName, entity, values,
                options.variables, options.initialTimeOutDuration(), options.pollIntervalDuration(),
                options.backoffDuration(), options.retries, options.fresh);
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

    /**
     * Extracts a single option value from local options, global defaults, or
     * returns the default value.
     *
     * @param fieldName the name of the option field
     * @param variables the current policy variables
     * @param options the local options expression result
     * @param defaultValue the default value to use if no valid value is found
     * @param extractor function to extract and validate the value from a
     * JsonNode
     * @param <T> the type of the option value
     * @return the resolved option value
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
