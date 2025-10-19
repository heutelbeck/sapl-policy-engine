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
package io.sapl.grammar.sapl.impl.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FunctionIdentifier;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.sapl.interpreter.context.AuthorizationContext.getAttributeStreamBroker;

/**
 * Factory for creating attribute finder invocations and evaluating attributes
 * in SAPL policies. Handles both environment attributes and entity-bound
 * attributes with configurable timeout, polling, and retry behavior.
 */
@UtilityClass
public class AttributeFactory {

    private static final String CONFIGURATION_ID_KEY = "CONFIGURATION_ID";

    private static final String                 DEFAULT_CONFIG_ID = "none";
    private static final AttributeFinderOptions DEFAULT_OPTIONS   = new AttributeFinderOptions(3000L, 30000L, 1000L, 3,
            false);

    private static final String EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR = "Attribute resolution error. Attributes not allowed in target.";
    private static final String UNDEFINED_VALUE_ERROR              = "Undefined value handed over as left-hand parameter to policy information point";

    private static final String OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS = "attributeFinderOptions";
    private static final String OPTION_FIELD_BACKOFF                  = "backoffMs";
    private static final String OPTION_FIELD_FRESH                    = "fresh";
    private static final String OPTION_FIELD_INITIAL_TIMEOUT          = "initialTimeOutMs";
    private static final String OPTION_FIELD_POLL_INTERVAL            = "pollIntervalMs";
    private static final String OPTION_FIELD_RETRIES                  = "retries";

    private static final String VARIABLE_NAME_SAPL = "SAPL";

    /**
     * Creates an attribute finder invocation for entity-bound attributes.
     *
     * @param ctx the reactive context containing configuration
     * and variables
     * @param attributeNameReference the fully qualified attribute name
     * @param entity the entity to which the attribute is bound
     * @param arguments the arguments for the attribute finder
     * @param options the options for controlling attribute finder
     * behavior
     * @return an attribute finder invocation configured with the provided
     * parameters
     */
    public static AttributeFinderInvocation attributeFinderInvocationFor(ContextView ctx, String attributeNameReference,
            Val entity, List<Val> arguments, Val options) {
        val configurationId = extractConfigurationId(ctx);
        val variables       = AuthorizationContext.getVariables(ctx);
        val finalOptions    = getOptions(variables, options);
        return new AttributeFinderInvocation(configurationId, attributeNameReference, entity, arguments, variables,
                finalOptions.initialTimeOutDuration(), finalOptions.pollIntervalDuration(),
                finalOptions.backoffDuration(), finalOptions.retries, finalOptions.fresh);
    }

    /**
     * Creates an attribute finder invocation for environment attributes.
     *
     * @param ctx the reactive context containing configuration
     * and variables
     * @param attributeNameReference the fully qualified attribute name
     * @param arguments the arguments for the attribute finder
     * @param options the options for controlling attribute finder
     * behavior
     * @return an attribute finder invocation configured with the provided
     * parameters
     */
    public static AttributeFinderInvocation environmentAttributeFinderInvocationFor(ContextView ctx,
            String attributeNameReference, List<Val> arguments, Val options) {
        val configurationId = extractConfigurationId(ctx);
        val variables       = AuthorizationContext.getVariables(ctx);
        val finalOptions    = getOptions(variables, options);
        return new AttributeFinderInvocation(configurationId, attributeNameReference, arguments, variables,
                finalOptions.initialTimeOutDuration(), finalOptions.pollIntervalDuration(),
                finalOptions.backoffDuration(), finalOptions.retries, finalOptions.fresh);
    }

    /**
     * Evaluates an entity-bound attribute expression by creating a reactive stream
     * of attribute values.
     *
     * @param source the source location in the policy document
     * @param identifier the attribute identifier
     * @param entity the entity to which the attribute is bound
     * @param arguments the attribute arguments
     * @param attributeFinderOptions the attribute finder configuration options
     * @return a flux of attribute values
     */
    public Flux<Val> evaluateAttribute(EObject source, FunctionIdentifier identifier, Val entity, Arguments arguments,
            Expression attributeFinderOptions) {
        return Flux.deferContextual(ctx -> {
            val resolvedAttributeName = FunctionUtil.resolveFunctionIdentifierByImports(source, identifier);

            if (entity.isError()) {
                return Flux.just(entity.withTrace(AttributeFinderStep.class, false,
                        Map.of(Trace.PARENT_VALUE, entity, Trace.ATTRIBUTE, Val.of(resolvedAttributeName))));
            }
            if (TargetExpressionUtil.isInTargetExpression(source)) {
                return Flux.just(ErrorFactory.error(source, EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR).withTrace(
                        AttributeFinderStep.class, false,
                        Map.of(Trace.PARENT_VALUE, entity, Trace.ATTRIBUTE, Val.of(resolvedAttributeName))));
            }
            if (entity.isUndefined()) {
                return Flux.just(ErrorFactory.error(source, UNDEFINED_VALUE_ERROR).withTrace(AttributeFinderStep.class,
                        false, Map.of(Trace.PARENT_VALUE, entity, Trace.ATTRIBUTE, Val.of(resolvedAttributeName))));
            }
            val attributeStreamBroker = getAttributeStreamBroker(ctx);

            if ((null != arguments && !arguments.getArgs().isEmpty()) || attributeFinderOptions != null) {
                val argumentsAndOptionsFluxes = FunctionUtil.combineArgumentFluxesAndOptions(arguments,
                        attributeFinderOptions);
                return argumentsAndOptionsFluxes.switchMap(argumentsAndOptions -> attributeStreamBroker
                        .attributeStream(attributeFinderInvocationFor(ctx, resolvedAttributeName, entity,
                                List.of(argumentsAndOptions.arguments()), argumentsAndOptions.options())));
            } else {
                return attributeStreamBroker.attributeStream(attributeFinderInvocationFor(ctx, resolvedAttributeName,
                        entity, List.of(), Val.ofEmptyObject()));
            }
        });
    }

    /**
     * Evaluates an environment attribute expression by creating a reactive stream
     * of attribute values.
     *
     * @param source the source location in the policy document
     * @param identifier the attribute identifier
     * @param arguments the attribute arguments
     * @param attributeFinderOptions the attribute finder configuration options
     * @return a flux of attribute values
     */
    public Flux<Val> evaluateEnvironmentAttribute(EObject source, FunctionIdentifier identifier, Arguments arguments,
            Expression attributeFinderOptions) {
        return Flux.deferContextual(ctx -> {
            val resolvedAttributeName = FunctionUtil.resolveFunctionIdentifierByImports(source, identifier);

            if (TargetExpressionUtil.isInTargetExpression(source))
                return Flux.just(ErrorFactory.error(source, EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR).withTrace(
                        AttributeFinderStep.class, false, Map.of(Trace.ATTRIBUTE, Val.of(resolvedAttributeName))));

            val attributeStreamBroker = AuthorizationContext.getAttributeStreamBroker(ctx);

            if ((null != arguments && !arguments.getArgs().isEmpty()) || attributeFinderOptions != null) {
                val argumentsAndOptionsFluxes = FunctionUtil.combineArgumentFluxesAndOptions(arguments,
                        attributeFinderOptions);
                return argumentsAndOptionsFluxes.switchMap(argumentsAndOptions -> attributeStreamBroker
                        .attributeStream(environmentAttributeFinderInvocationFor(ctx, resolvedAttributeName,
                                List.of(argumentsAndOptions.arguments()), argumentsAndOptions.options())));
            } else {
                return attributeStreamBroker.attributeStream(environmentAttributeFinderInvocationFor(ctx,
                        resolvedAttributeName, List.of(), Val.ofEmptyObject()));
            }
        });
    }

    /**
     * Extracts the PDP configuration identifier from the reactive context.
     *
     * @param ctx the reactive context
     * @return the configuration identifier or default if not present
     */
    private static String extractConfigurationId(ContextView ctx) {
        if (ctx.hasKey(CONFIGURATION_ID_KEY)) {
            val configIdValue = ctx.get(CONFIGURATION_ID_KEY);
            if (configIdValue instanceof String configIdString) {
                return configIdString;
            }
        }
        return DEFAULT_CONFIG_ID;
    }

    /**
     * Retrieves global attribute finder defaults from the SAPL variable context.
     *
     * @param variables the current policy variables
     * @return an optional containing the global defaults object, or empty if not
     * present
     */
    private static Optional<ObjectNode> globalDefaults(Map<String, Val> variables) {
        val saplOptions = variables.get(VARIABLE_NAME_SAPL);
        if (saplOptions == null || !saplOptions.isObject()) {
            return Optional.empty();
        }
        val attributeFinderOptions = saplOptions.getObjectNode().get(OPTION_FIELD_ATTRIBUTE_FINDER_OPTIONS);
        if (attributeFinderOptions == null || !attributeFinderOptions.isObject()) {
            return Optional.empty();
        }
        return Optional.of((ObjectNode) attributeFinderOptions);
    }

    /**
     * Resolves attribute finder options from local options, global defaults, or
     * built-in defaults.
     *
     * @param variables the current policy variables
     * @param options the local options expression result
     * @return the resolved attribute finder options
     */
    private static AttributeFinderOptions getOptions(Map<String, Val> variables, Val options) {
        return new AttributeFinderOptions(
                optionValue(OPTION_FIELD_INITIAL_TIMEOUT, variables, options, DEFAULT_OPTIONS.initialTimeOutMs,
                        n -> n.isNumber() ? n.asLong() : null),
                optionValue(OPTION_FIELD_POLL_INTERVAL, variables, options, DEFAULT_OPTIONS.pollIntervalMs,
                        n -> n.isNumber() ? n.asLong() : null),
                optionValue(OPTION_FIELD_BACKOFF, variables, options, DEFAULT_OPTIONS.backoffMs,
                        n -> n.isNumber() ? n.asLong() : null),
                optionValue(OPTION_FIELD_RETRIES, variables, options, DEFAULT_OPTIONS.retries,
                        n -> n.canConvertToInt() ? n.asInt() : null),
                optionValue(OPTION_FIELD_FRESH, variables, options, DEFAULT_OPTIONS.fresh,
                        n -> n.isBoolean() ? n.asBoolean() : null));
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
    private static <T> @NonNull T optionValue(String fieldName, Map<String, Val> variables, Val options,
            @NonNull T defaultValue, Function<JsonNode, T> extractor) {
        if (options != null && options.isObject()) {
            val node = options.getObjectNode().get(fieldName);
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
     * Configuration options for attribute finder behavior including timeouts,
     * polling, and retry settings.
     *
     * @param initialTimeOutMs the initial timeout in milliseconds before retrying
     * @param pollIntervalMs the interval in milliseconds for polling streaming
     * attributes
     * @param backoffMs the backoff duration in milliseconds between retry
     * attempts
     * @param retries the maximum number of retry attempts
     * @param fresh whether to bypass caching and always fetch fresh
     * values
     */
    private record AttributeFinderOptions(
            long initialTimeOutMs,
            long pollIntervalMs,
            long backoffMs,
            int retries,
            boolean fresh) {
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
