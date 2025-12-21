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
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueMetadata;
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.grammar.antlr.SAPLParser.ArgumentsContext;
import io.sapl.grammar.antlr.SAPLParser.AttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentHeadAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderStepContext;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.time.Instant;
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
 * invocations. Type mismatches in options silently fall through to next
 * precedence level. UNDEFINED entities rejected for attribute steps, allowed
 * for environment attributes. {@link AttributeBroker} must be present in
 * reactor context (runtime requirement).
 */
@UtilityClass
public class AttributeCompiler {

    /** Defaults: 3s timeout, 30s poll, 1s backoff, 3 retries, caching allowed. */
    private static final AttributeFinderOptions DEFAULT_OPTIONS = new AttributeFinderOptions("none", 3000L, 30000L,
            1000L, 3, false, null, null);

    private static final String COMPILE_ERROR_PIP_RETURNED_PURE_EXPRESSION = "Compilation failed. Got PureExpression from PIP. Indicates implementation bug.";
    private static final String COMPILE_ERROR_PIP_RETURNED_VALUE           = "Compilation failed. Got Value from PIP. Indicates implementation bug.";
    private static final String COMPILE_ERROR_UNDEFINED_VALUE_LEFT_HAND    = "Compilation failed. Undefined value handed over as left-hand parameter to policy information point.";

    private static final String RUNTIME_ERROR_ATTRIBUTE_BROKER_NOT_CONFIGURED   = "Internal PDP Error. AttributeBroker not configured in evaluation context.";
    private static final String RUNTIME_ERROR_ATTRIBUTE_PARAMETERS_INSUFFICIENT = "Internal PDP Error. Attribute evaluation must have at least two parameters, but got %d.";
    private static final String RUNTIME_ERROR_UNDEFINED_VALUE_LEFT_HAND         = "Undefined value handed over as left-hand parameter to policy information point.";

    private static final String OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS = "attributeFinderOptions";
    private static final String OPTION_FIELD_BACKOFF                  = "backoffMs";
    private static final String OPTION_FIELD_FRESH                    = "fresh";
    private static final String OPTION_FIELD_INITIAL_TIMEOUT          = "initialTimeOutMs";
    private static final String OPTION_FIELD_POLL_INTERVAL            = "pollIntervalMs";
    private static final String OPTION_FIELD_RETRIES                  = "retries";
    private static final String VARIABLE_NAME_SAPL                    = "SAPL";

    /**
     * Compiles attribute finder step (e.g., {@code entity.<pip.attr>}). Entity
     * becomes first parameter. UNDEFINED entity rejected at runtime.
     *
     * @param entity the compiled entity expression (left-hand side)
     * @param attributeFinderStep the AST node for the attribute finder step
     * @param context the compilation context
     * @return a {@link StreamExpression} that invokes the PIP
     */
    public static CompiledExpression compileAttributeFinderStep(CompiledExpression entity,
            AttributeFinderStepContext attributeFinderStep, CompilationContext context) {
        return compileAttributeFinderStep(attributeFinderStep, entity, attributeFinderStep.functionIdentifier(),
                attributeFinderStep.arguments(), attributeFinderStep.attributeFinderOptions, context);
    }

    /**
     * Compiles environment attribute (e.g., {@code <time.now>}). Entity set to
     * UNDEFINED, which is allowed for environment attributes.
     *
     * @param envAttribute the AST node for the environment attribute
     * @param context the compilation context
     * @return a {@link StreamExpression} that invokes the PIP
     */
    public static CompiledExpression compileEnvironmentAttribute(BasicEnvironmentAttributeContext envAttribute,
            CompilationContext context) {
        return compileAttributeFinderStep(envAttribute, null, envAttribute.functionIdentifier(),
                envAttribute.arguments(), envAttribute.attributeFinderOptions, context);
    }

    /**
     * Compiles head attribute finder step (e.g., {@code entity.|<pip.attr>}).
     * Applies {@code take(1)} to return only the first value. Upstream may not
     * cancel immediately due to reactive backpressure.
     *
     * @param entity the compiled entity expression (left-hand side)
     * @param attributeFinderStep the AST node for the head attribute finder step
     * @param context the compilation context
     * @return a {@link StreamExpression} that emits only the first PIP value
     */
    public static CompiledExpression compileHeadAttributeFinderStep(CompiledExpression entity,
            HeadAttributeFinderStepContext attributeFinderStep, CompilationContext context) {
        val fullStream = compileAttributeFinderStep(attributeFinderStep, entity,
                attributeFinderStep.functionIdentifier(), attributeFinderStep.arguments(),
                attributeFinderStep.attributeFinderOptions, context);
        return fullStreamToHead(attributeFinderStep, fullStream);
    }

    /**
     * Compiles head environment attribute (e.g., {@code |<time.now>}). Applies
     * {@code take(1)} to return only the first value.
     *
     * @param envAttribute the AST node for the head environment attribute
     * @param context the compilation context
     * @return a {@link StreamExpression} that emits only the first PIP value
     */
    public static CompiledExpression compileHeadEnvironmentAttribute(BasicEnvironmentHeadAttributeContext envAttribute,
            CompilationContext context) {
        val fullStream = compileAttributeFinderStep(envAttribute, null, envAttribute.functionIdentifier(),
                envAttribute.arguments(), envAttribute.attributeFinderOptions, context);
        return fullStreamToHead(envAttribute, fullStream);
    }

    /**
     * Applies {@code take(1)} to stream. Throws if not {@link StreamExpression}
     * (implementation bug).
     */
    private CompiledExpression fullStreamToHead(ParserRuleContext astNode, CompiledExpression fullStream) {
        return switch (fullStream) {
        case ErrorValue error                     -> error;
        case Value ignored                        ->
            throw new SaplCompilerException(COMPILE_ERROR_PIP_RETURNED_VALUE, astNode);
        case PureExpression ignored               ->
            throw new SaplCompilerException(COMPILE_ERROR_PIP_RETURNED_PURE_EXPRESSION, astNode);
        case StreamExpression(Flux<Value> stream) -> new StreamExpression(stream.take(1));
        };
    }

    /**
     * Core method. Uses {@code combineLatest} + {@code switchMap}. Null
     * options/arguments replaced with empty. Options deferred via
     * {@code deferContextual}. Parameter array: [entity, options, args...].
     */
    private static CompiledExpression compileAttributeFinderStep(ParserRuleContext astNode, CompiledExpression entity,
            FunctionIdentifierContext identifier, ArgumentsContext stepArguments,
            ExpressionContext attributeFinderOptions, CompilationContext context) {
        if (entity instanceof ErrorValue) {
            return entity;
        }
        var compiledOptions = ExpressionCompiler.compileExpression(attributeFinderOptions, context);
        if (compiledOptions == null) {
            compiledOptions = Value.EMPTY_OBJECT;
        }
        if (entity instanceof UndefinedValue) {
            throw new SaplCompilerException(COMPILE_ERROR_UNDEFINED_VALUE_LEFT_HAND, astNode);
        }
        val entityFlux           = entity == null ? Flux.just(Value.NULL)
                : ExpressionCompiler.compiledExpressionToFlux(entity);
        val optionsParameterFlux = ExpressionCompiler.compiledExpressionToFlux(compiledOptions);
        val effectiveOptionsFlux = Flux.deferContextual(
                ctx -> optionsParameterFlux.map(optionsParameterValue -> calculateOptions(ctx, optionsParameterValue)));

        var arguments = CompiledArguments.EMPTY_ARGUMENTS;
        if (stepArguments != null && stepArguments.args != null) {
            arguments = ExpressionCompiler.compileArguments(stepArguments.args, context);
        }

        val attributeFinderParameterSources = new ArrayList<Flux<?>>(arguments.arguments().length + 2);
        attributeFinderParameterSources.add(entityFlux);
        attributeFinderParameterSources.add(effectiveOptionsFlux);
        for (val argument : arguments.arguments()) {
            attributeFinderParameterSources.add(ExpressionCompiler.compiledExpressionToFlux(argument));
        }

        val resolvedAttributeName = resolveAttributeName(identifier, context);

        val attributeStream = Flux.combineLatest(attributeFinderParameterSources, Function.identity()).switchMap(
                combined -> evaluatedAttributeFinder(astNode, combined, resolvedAttributeName, entity == null));
        return new StreamExpression(attributeStream);
    }

    /**
     * Extracts the attribute name from the function identifier and resolves it
     * using imports.
     */
    private static String resolveAttributeName(FunctionIdentifierContext identifier, CompilationContext context) {
        if (identifier == null || identifier.idFragment == null || identifier.idFragment.isEmpty()) {
            return "";
        }
        val fragments = identifier.idFragment;
        val builder   = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(ExpressionCompiler.getIdentifierName(fragments.get(i)));
        }
        val rawName = builder.toString();
        return context.resolveFunctionName(rawName);
    }

    /**
     * Validates params, delegates to AttributeBroker. Parameters: [entity, options,
     * args...]. Environment attributes pass entity as null (not UNDEFINED).
     * Unchecked casts - ClassCastException if compilation bug.
     */
    private static Flux<Value> evaluatedAttributeFinder(ParserRuleContext astNode,
            Object[] evaluatedAttributeFinderParameters, String attributeName, boolean isEnvironmentAttribute) {
        var metadata = mergeMetadataFromAttributeParameters(evaluatedAttributeFinderParameters);
        if (evaluatedAttributeFinderParameters.length < 2) {
            return Flux.just(Error.at(astNode, metadata, RUNTIME_ERROR_ATTRIBUTE_PARAMETERS_INSUFFICIENT
                    .formatted(evaluatedAttributeFinderParameters.length)));
        }
        val entity = (Value) evaluatedAttributeFinderParameters[0];

        if (entity instanceof ErrorValue) {
            return Flux.just(entity.withMetadata(metadata));
        }
        if (!isEnvironmentAttribute && entity instanceof UndefinedValue) {
            return Flux.just(Error.at(astNode, metadata, RUNTIME_ERROR_UNDEFINED_VALUE_LEFT_HAND));
        }
        val maybeOptions = (Options) evaluatedAttributeFinderParameters[1];
        if (maybeOptions instanceof OptionsError(ErrorValue error)) {
            return Flux.just(error.withMetadata(metadata));
        }

        val options = (AttributeFinderOptions) maybeOptions;
        if (options.attributeBroker == null) {
            return Flux.just(Error.at(astNode, metadata, RUNTIME_ERROR_ATTRIBUTE_BROKER_NOT_CONFIGURED));
        }

        var values        = Arrays
                .stream(evaluatedAttributeFinderParameters, 2, evaluatedAttributeFinderParameters.length)
                .map(Value.class::cast).toList();
        val invocation    = new AttributeFinderInvocation(options.configurationId, attributeName,
                isEnvironmentAttribute ? null : entity, values, options.variables, options.initialTimeOutDuration(),
                options.pollIntervalDuration(), options.backoffDuration(), options.retries, options.fresh);
        val inputMetadata = metadata;
        return options.attributeBroker.attributeStream(invocation).map(attribute -> {
            val location = SourceLocationUtil.fromContext(astNode);
            if (attribute instanceof ErrorValue error && error.location() == null) {
                attribute = error.withLocation(location);
            }

            val attributeRecord   = new AttributeRecord(invocation, attribute, Instant.now(), location);
            val attributeMetadata = inputMetadata.merge(ValueMetadata.ofAttribute(attributeRecord));
            return attribute.withMetadata(attributeMetadata);
        });
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
    private Optional<Long> extractLong(Value value) {
        if (value instanceof NumberValue number) {
            return Optional.of(number.value().longValue());
        }
        return Optional.empty();
    }

    /** BigDecimal to int - truncates fractional, may overflow. */
    private Optional<Integer> extractInteger(Value value) {
        if (value instanceof NumberValue number) {
            return Optional.of(number.value().intValue());
        }
        return Optional.empty();
    }

    private Optional<Boolean> extractBoolean(Value value) {
        if (value instanceof BooleanValue bool) {
            return Optional.of(bool.value());
        }
        return Optional.empty();
    }

    /**
     * Precedence: inline > global > default. Type mismatches silently fall through
     * to next level.
     */
    private static <T> @NonNull T optionValue(String fieldName, Map<String, Value> variables, Value options,
            @NonNull T defaultValue, Function<Value, Optional<T>> extractor) {
        if (options instanceof ObjectValue optionsObject) {
            val node = optionsObject.get(fieldName);
            if (node != null) {
                val value = extractor.apply(node);
                if (value.isPresent()) {
                    return value.get();
                }
            }
        }

        val maybeGlobal = globalDefaults(variables);
        if (maybeGlobal.isPresent()) {
            val globalNode = maybeGlobal.get().get(fieldName);
            if (globalNode != null) {
                val value = extractor.apply(globalNode);
                if (value.isPresent()) {
                    return value.get();
                }
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
     * Resolved PIP invocation security. {@code configurationId} from
     * EvaluationContext. {@code backoffMs} is exponential base. {@code retries} is
     * additional attempts (3 = 4 total).
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

    /**
     * Merges metadata from Value elements in an attribute finder parameter array.
     * Array structure: [entity (Value), options (Options), args... (Value)]. Skips
     * index 1 (options) since it's not a Value.
     */
    private static ValueMetadata mergeMetadataFromAttributeParameters(Object[] elements) {
        if (elements.length == 0) {
            return ValueMetadata.EMPTY;
        }
        // Index 0 is entity (Value)
        var result = ((Value) elements[0]).metadata();
        // Index 1 is options (not a Value), skip it
        // Index 2+ are args (Value)
        for (int i = 2; i < elements.length; i++) {
            result = result.merge(((Value) elements[i]).metadata());
        }
        return result;
    }

}
