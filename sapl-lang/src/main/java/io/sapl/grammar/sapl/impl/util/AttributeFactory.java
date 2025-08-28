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

import static io.sapl.interpreter.context.AuthorizationContext.getAttributeStreamBroker;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.FunctionIdentifier;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

@UtilityClass
public class AttributeFactory {
    private static final String CONFIGURATION_ID                   = "CONFIGURATION_ID";
    private static final String EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR = "Attribute resolution error. Attributes not allowed in target.";
    private static final String UNDEFINED_VALUE_ERROR              = "Undefined value handed over as left-hand parameter to policy information point";

    public static AttributeFinderInvocation attributeFinderInvocationFor(ContextView ctx, String attributeNameReference,
            Val entity, List<Val> arguments) {
        // TODO: Introduce meaningful default values to context and make them
        // configurable
        var pdpConfigurationId = "none";
        if (ctx.hasKey(CONFIGURATION_ID)) {
            var configIdValue = ctx.get(CONFIGURATION_ID);
            if (configIdValue instanceof String configIdString) {
                pdpConfigurationId = configIdString;
            }
        }
        final var variables      = AuthorizationContext.getVariables(ctx);
        final var initialTimeOut = Duration.ofMillis(100L);
        final var pollIntervall  = Duration.ofMillis(500L);
        final var backoff        = Duration.ofMillis(1000L);
        final var retries        = 100;
        final var fresh          = true;
        return new AttributeFinderInvocation(pdpConfigurationId, attributeNameReference, entity, arguments, variables,
                initialTimeOut, pollIntervall, backoff, retries, fresh);
    }

    public static AttributeFinderInvocation environmentAttributeFinderInvocationFor(ContextView ctx,
            String attributeNameReference, List<Val> arguments) {
        // TODO: Introduce meaningful default values to context and make them
        // configurable
        final var pdpConfigurationId = "none";
        final var variables          = AuthorizationContext.getVariables(ctx);
        final var initialTimeOut     = Duration.ofMillis(100L);
        final var pollIntervall      = Duration.ofMillis(500L);
        final var backoff            = Duration.ofMillis(1000L);
        final var retries            = 100;
        final var fresh              = true;
        return new AttributeFinderInvocation(pdpConfigurationId, attributeNameReference, arguments, variables,
                initialTimeOut, pollIntervall, backoff, retries, fresh);
    }

    public Flux<Val> evaluateEnvironmentAttibute(EObject source, FunctionIdentifier identifier, Arguments arguments) {
        return Flux.deferContextual(ctx -> {
            final var resolvedAttributeName = FunctionUtil.resolveFunctionIdentifierByImports(source, identifier);

            if (TargetExpressionUtil.isInTargetExpression(source))
                return Flux.just(ErrorFactory.error(source, EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR).withTrace(
                        AttributeFinderStep.class, false, Map.of(Trace.ATTRIBUTE, Val.of(resolvedAttributeName))));

            final var attributeStreamBroker = AuthorizationContext.getAttributeStreamBroker(ctx);

            if (null != arguments && !arguments.getArgs().isEmpty()) {
                final var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments).map(List::of);
                return argumentFluxes.switchMap(argumentsList -> attributeStreamBroker.attributeStream(
                        environmentAttributeFinderInvocationFor(ctx, resolvedAttributeName, argumentsList)));
            } else {
                return attributeStreamBroker.attributeStream(
                        environmentAttributeFinderInvocationFor(ctx, resolvedAttributeName, List.of()));
            }
        });
    }

    public Flux<Val> evaluateAttibute(EObject source, FunctionIdentifier identifier, Val entity, Arguments arguments) {
        return Flux.deferContextual(ctx -> {
            final var resolvedAttributeName = FunctionUtil.resolveFunctionIdentifierByImports(source, identifier);

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
            final var attributeStreamBroker = getAttributeStreamBroker(ctx);
            if (null != arguments && !arguments.getArgs().isEmpty()) {
                final var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments).map(List::of);
                return argumentFluxes.switchMap(argumentsList -> attributeStreamBroker.attributeStream(
                        attributeFinderInvocationFor(ctx, resolvedAttributeName, entity, argumentsList)));
            } else {
                final var invocation = attributeFinderInvocationFor(ctx, resolvedAttributeName, entity, List.of());
                return attributeStreamBroker.attributeStream(invocation);
            }
        });
    }
}
