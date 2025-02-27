package io.sapl.grammar.sapl.impl.util;

import static io.sapl.interpreter.context.AuthorizationContext.getAttributeStreamBroker;
import static io.sapl.interpreter.context.AuthorizationContext.getImports;

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
    private static final String EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR        = "Attribute resolution error. Attributes not allowed in target.";
    private static final String UNDEFINED_VALUE_ERROR                     = "Undefined value handed over as left-hand parameter to policy information point";

    public static AttributeFinderInvocation attributeFinderInvocationFor(ContextView ctx,
            String fullyQualifiedAttributeName, Val entity, List<Val> arguments) {
        // TODO: Introduce meaningful default values to context and make them
        // configurable
        final var pdpConfigurationId = "none";
        final var variables          = AuthorizationContext.getVariables(ctx);
        final var initialTimeOut     = Duration.ofMillis(100L);
        final var pollIntervall      = Duration.ofMillis(500L);
        final var backoff            = Duration.ofMillis(1000L);
        final var retries            = 100;
        final var fresh              = true;
        return new AttributeFinderInvocation(pdpConfigurationId, fullyQualifiedAttributeName, entity, arguments,
                variables, initialTimeOut, pollIntervall, backoff, retries, fresh);
    }

    public static AttributeFinderInvocation environmentAttributeFinderInvocationFor(ContextView ctx,
            String fullyQualifiedAttributeName, List<Val> arguments) {
        // TODO: Introduce meaningful default values to context and make them
        // configurable
        final var pdpConfigurationId = "none";
        final var variables          = AuthorizationContext.getVariables(ctx);
        final var initialTimeOut     = Duration.ofMillis(100L);
        final var pollIntervall      = Duration.ofMillis(500L);
        final var backoff            = Duration.ofMillis(1000L);
        final var retries            = 100;
        final var fresh              = true;
        return new AttributeFinderInvocation(pdpConfigurationId, fullyQualifiedAttributeName, arguments, variables,
                initialTimeOut, pollIntervall, backoff, retries, fresh);
    }

    public Flux<Val> evaluateEnvironmentAttibute(EObject source, FunctionIdentifier identifier, Arguments arguments) {
        return Flux.deferContextual(ctx -> {
            final var attributeName = FunctionUtil.resolveAbsoluteFunctionName(identifier,
                    AuthorizationContext.getImports(ctx));

            if (TargetExpressionUtil.isInTargetExpression(source))
                return Flux.just(ErrorFactory.error(source, EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR)
                        .withTrace(AttributeFinderStep.class, false, Map.of(Trace.ATTRIBUTE, Val.of(attributeName))));

            final var attributeStreamBroker = AuthorizationContext.getAttributeStreamBroker(ctx);

            if (null != arguments && !arguments.getArgs().isEmpty()) {
                final var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments).map(List::of);
                return argumentFluxes.switchMap(argumentsList -> attributeStreamBroker
                        .attributeStream(environmentAttributeFinderInvocationFor(ctx, attributeName, argumentsList)));
            } else {
                return attributeStreamBroker
                        .attributeStream(environmentAttributeFinderInvocationFor(ctx, attributeName, List.of()));
            }
        });
    }

    public Flux<Val> evaluateAttibute(EObject source, FunctionIdentifier identifier, Val entity, Arguments arguments) {

        return Flux.deferContextual(ctx -> {
            final var attributeName = FunctionUtil.resolveAbsoluteFunctionName(identifier, getImports(ctx));

            if (entity.isError()) {
                return Flux.just(entity.withTrace(AttributeFinderStep.class, false,
                        Map.of(Trace.PARENT_VALUE, entity, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            if (TargetExpressionUtil.isInTargetExpression(source)) {
                return Flux.just(ErrorFactory.error(source, EXTERNAL_ATTRIBUTE_IN_TARGET_ERROR).withTrace(
                        AttributeFinderStep.class, false,
                        Map.of(Trace.PARENT_VALUE, entity, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            if (entity.isUndefined()) {
                return Flux.just(ErrorFactory.error(source, UNDEFINED_VALUE_ERROR).withTrace(AttributeFinderStep.class,
                        false, Map.of(Trace.PARENT_VALUE, entity, Trace.ATTRIBUTE, Val.of(attributeName))));
            }
            final var attributeStreamBroker = getAttributeStreamBroker(ctx);
            if (null != arguments && !arguments.getArgs().isEmpty()) {
                final var argumentFluxes = FunctionUtil.combineArgumentFluxes(arguments).map(List::of);
                return argumentFluxes.switchMap(argumentsList -> attributeStreamBroker
                        .attributeStream(attributeFinderInvocationFor(ctx, attributeName, entity, argumentsList)));
            } else {
                return attributeStreamBroker
                        .attributeStream(attributeFinderInvocationFor(ctx, attributeName, entity, List.of()));
            }
        });
    }
}
