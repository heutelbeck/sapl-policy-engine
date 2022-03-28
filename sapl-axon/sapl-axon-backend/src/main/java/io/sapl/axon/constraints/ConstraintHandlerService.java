/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.axon.constraints;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotations.ConstraintHandler;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

/**
 * This class provides methods for handling constraints of a {@link AuthorizationDecision}.
 * Constraints are handled by constraint handlers. These constraint handlers are either delivered autowired constraint
 * handler providers or methods in aggregate root or aggregate member objects that are annotated
 * with a {@link ConstraintHandler} annotation.
 * Handlers are filter according the stage of handling (pre- or post-message handling), responsibility and support given
 * the provided constraints.
 * Handlers are then added to an instance of the {@link AxonConstraintHandlerBundle} class for later execution.
 * In case of obligations an {@link AccessDeniedException} is thrown if they cannot be handled successfully.
 */

@Service
public class ConstraintHandlerService {
    private final List<AxonRunnableConstraintHandlerProvider> globalSimpleRunnableProviders;
    private final List<MappingConstraintHandlerProvider<?>> globalMappingProviders;
    private final List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

    private final List<MetaDataSupplierConstraintHandlerProvider<?>> globalAddMetaDataProviders;
    private final List<MessagePayloadMappingConstraintHandlerProvider<?>> globalMessageMappingProviders;
    private final List<MessageConsumerConstraintHandlerProvider<?, ? extends Message<?>>> globalMessageConsumerProviders;


    public ConstraintHandlerService(List<AxonRunnableConstraintHandlerProvider> globalSimpleRunnableProviders,
                                    List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
                                    List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders,
                                    List<MetaDataSupplierConstraintHandlerProvider<?>> globalAddMetaDataProvider,
                                    List<MessagePayloadMappingConstraintHandlerProvider<?>> globalMessageMappingProviders,
                                    List<MessageConsumerConstraintHandlerProvider<?, ? extends Message<?>>> globalMessageConsumerProviders
                                    ) {

        // sort according HasPriority
        this.globalSimpleRunnableProviders = globalSimpleRunnableProviders;
        Collections.sort(this.globalSimpleRunnableProviders);
        this.globalMessageConsumerProviders = globalMessageConsumerProviders;
        Collections.sort(this.globalMessageConsumerProviders);
        this.globalConsumerProviders = globalConsumerProviders;
        Collections.sort(this.globalConsumerProviders);
        this.globalAddMetaDataProviders = globalAddMetaDataProvider;
        Collections.sort(this.globalAddMetaDataProviders);
        this.globalMessageMappingProviders = globalMessageMappingProviders;
        Collections.sort(this.globalMessageMappingProviders);
        this.globalMappingProviders = globalMappingProviders;
        Collections.sort(this.globalMappingProviders);
    }

    /**
     * Creates an AxonConstraintHandlerBundle and adds all responsible and supporting constraint handlers for constraints
     * in the given AuthorizationDecision.
     *
     * @param <C>          PayloadType of the message
     * @param <R>          Expected ResultType of the handled command
     * @param <U>          MessageType
     * @param decision     AuthorizationDecision from the PDP
     * @param message      Handled command message
     * @param responseType ResponseType for the handled command message
     * @param aggregateConstraintMethods methods in aggregate root annotated with ConstraintHandler annotation
     * @param entity       Aggregate Member, provided only if command is handled by aggregate member
     * @param entityConstraintMethods methods in aggregate member annotated with ConstraintHandler annotation,
     *                                provided only if command is handled by aggregate member
     *
     * @return AxonConstraintHandlerBundle containing all ConstraintHandlers responsible for and supporting
     * Obligations and Advices of the given decision
     */

    public <C, R, U extends Message<C>> AxonConstraintHandlerBundle<C, R, U> createCommandBundle(
            AuthorizationDecision decision, U message, Class<R> responseType,
            Optional<List<Method>> aggregateConstraintMethods, Optional<Object> entity,
            Optional<List<Method>> entityConstraintMethods) {
        var bundle = new AxonConstraintHandlerBundle<C, R, U>();
        failIfResourcePresent(decision);
        decision.getObligations().ifPresent(obligations -> obligations.forEach(obligation -> {
            var numberOfHandlers = addPreHandleConstraintHandler(bundle, obligation, true, message);
            numberOfHandlers += addPostHandleConstraintHandler(bundle, obligation, true, responseType);
            numberOfHandlers += addAggregateConstraintHandlers(bundle, obligation, true, message,
                    aggregateConstraintMethods, entity, entityConstraintMethods);

            if (numberOfHandlers == 0)
                throw new AccessDeniedException(
                        String.format("No handler found for obligation: %s", obligation.asText()));
        }));

        decision.getAdvice().ifPresent(advices -> advices.forEach(advice -> {
            addPreHandleConstraintHandler(bundle, advice, false, message);
            addPostHandleConstraintHandler(bundle, advice, false, responseType);
            addAggregateConstraintHandlers(bundle, advice, false, message, aggregateConstraintMethods, entity, entityConstraintMethods);
        }));

        return bundle;
    }


    /**
     * Creates an AxonConstraintHandlerBundle and adds all responsible and supporting constraint handlers for constraints
     * in the given AuthorizationDecision.
     *
     * @param <Q>          PayloadType of the query
     * @param <R>          Expected ResultType of the handled query
     * @param <U>          MessageType
     * @param decision     AuthorizationDecision from the PDP
     * @param message      Handled query message
     * @param responseType ResponseType for the handled query message
     *
     * @return AxonConstraintHandlerBundle containing all ConstraintHandlers responsible and supporting the
     * Obligations and Advices of the given decision
     */
    public <Q, R, U extends Message<Q>> AxonConstraintHandlerBundle<Q, R, U> createQueryBundle(
            AuthorizationDecision decision, U message, ResponseType<R> responseType) {
    	var bundle = new AxonConstraintHandlerBundle<Q, R, U>();
        decision.getObligations().ifPresent(obligations -> obligations.forEach(obligation -> {
            var numberOfHandlers = addPreHandleConstraintHandler(bundle, obligation, true, message);
            numberOfHandlers += addPostHandleConstraintHandler(bundle, obligation, true,
                    responseType.responseMessagePayloadType());
            if (numberOfHandlers == 0)
                throw new AccessDeniedException(
                        String.format("No handler found for obligation: %s", obligation.asText()));
        }));

        decision.getAdvice().ifPresent(advices -> advices.forEach(advice -> {
            addPreHandleConstraintHandler(bundle, advice, false, message);
            addPostHandleConstraintHandler(bundle, advice, false, responseType.responseMessagePayloadType());
        }));

        return bundle;

    }

    private <Q, R, U extends Message<Q>> int addAggregateConstraintHandlers(AxonConstraintHandlerBundle<Q, R, U> bundle,
                                                                            JsonNode constraint, boolean isObligation, U message,
                                                                            Optional<List<Method>> aggregateConstraintMethods, Optional<Object> entity,
                                                                            Optional<List<Method>> entityConstraintMethods) {
        int numberOfHandlers = 0;
        var aggregateRootHandlers = collectAggregateConstraintHandlerMethods(aggregateConstraintMethods, constraint, message);
        bundle.aggregateRootHandlers.put(constraint, aggregateRootHandlers);

        numberOfHandlers += aggregateRootHandlers.size();

        if (entity.isPresent()) {
            var aggregateEntityHandlers = collectAggregateConstraintHandlerMethods(entityConstraintMethods, constraint, message);
            bundle.aggregateMemberHandlers.put(constraint, aggregateEntityHandlers);
            numberOfHandlers += aggregateEntityHandlers.size();
        }

        if (numberOfHandlers > 0) {
            bundle.isObligationPerConstraint.put(constraint, isObligation);
        }

        return numberOfHandlers;
    }

    private <U extends Message<?>> boolean isMethodResponsibleForConstraintHandling(JsonNode constraint, U message, Method commandHandlingMethod) {
        ConstraintHandler annotation = commandHandlingMethod.getAnnotation(ConstraintHandler.class);
        var annotationValue = annotation.value();

        if (annotationValue.isBlank()) {
            return true;
        }

        var context = new StandardEvaluationContext();
        context.setVariable("constraint", constraint);
        context.setVariable("commandMessage", message);
        var expressionParser = new SpelExpressionParser();
        var expression = expressionParser.parseExpression(annotationValue);

        try {
            var value = expression.getValue(context);
            if (value == null) {
                return false;
            }
            return (Boolean) value;
        }
        catch (SpelEvaluationException |NullPointerException e) {
            return false;
        }
    }

    private <Q, R, U extends Message<Q>> int addPreHandleConstraintHandler(AxonConstraintHandlerBundle<Q, R, U> bundle,
                                                                           JsonNode constraint, boolean isObligation, U message) {
        int numberOfHandlers = 0;

        var simpleRunnableHandlers = constructSimpleRunnableHandlersForConstraint(constraint, isObligation,
                AxonRunnableConstraintHandlerProvider.Signal.PRE_HANDLE);
        bundle.simpleRunnableHandlers.addAll(simpleRunnableHandlers);
        numberOfHandlers += simpleRunnableHandlers.size();

        var addMetaDataHandlers = constructAddMetaDataHandlersForConstraint(constraint, isObligation, message);
        bundle.addMetaDataHandlers.addAll(addMetaDataHandlers);
        numberOfHandlers += addMetaDataHandlers.size();

        var messageConsumerHandlers = constructMessageConsumerHandlersForConstraint(constraint, isObligation, message);
        bundle.messageConsumerHandlers.addAll(messageConsumerHandlers);
        numberOfHandlers += messageConsumerHandlers.size();

        var messageMappingHandlers = constructMessageMappingHandlersForConstraint(constraint, isObligation, message);
        bundle.messagePayloadMappingHandlers.addAll(messageMappingHandlers);
        numberOfHandlers += messageMappingHandlers.size();

        return numberOfHandlers;
    }

    private <Q, R> int addPostHandleConstraintHandler(AxonConstraintHandlerBundle<Q, R, ?> bundle, JsonNode constraint,
                                                      boolean isObligation, Class<R> responseType) {
        int numberOfHandlers = 0;

        var simpleRunnableHandlers = constructSimpleRunnableHandlersForConstraint(constraint, isObligation,
                AxonRunnableConstraintHandlerProvider.Signal.POST_HANDLE);
        bundle.simpleRunnableHandlers.addAll(simpleRunnableHandlers);
        numberOfHandlers += simpleRunnableHandlers.size();

        var consumerHandlers = constructResultConsumerHandlersForConstraint(constraint, isObligation, responseType);
        bundle.resultConsumerHandlers.addAll(consumerHandlers);
        numberOfHandlers += consumerHandlers.size();

        var mappingHandlers = constructResultMappingHandlersForConstraint(constraint, isObligation, responseType);
        bundle.resultMappingHandlers.addAll(mappingHandlers);
        numberOfHandlers += mappingHandlers.size();

        return numberOfHandlers;
    }

    @SuppressWarnings("unchecked")
    private <U extends Message<?>> List<Consumer<U>> constructMessageConsumerHandlersForConstraint(
            JsonNode constraint, boolean isObligation, U message) {

        return globalMessageConsumerProviders.stream().filter(provider -> provider.isResponsible(constraint))
                .filter(provider -> provider.supports(message))
                .map(provider -> ((MessageConsumerConstraintHandlerProvider<?, U>) provider).getHandler(constraint))
                .map(failConsumerOnlyIfObligation(isObligation)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T, U extends Message<T>> List<Function<T, T>> constructMessageMappingHandlersForConstraint(
            JsonNode constraint, boolean isObligation, U message) {
        return globalMessageMappingProviders.stream().filter(provider -> provider.isResponsible(constraint))
                .filter(provider -> provider.supports(message))
                .map(provider -> ((MessagePayloadMappingConstraintHandlerProvider<T>) provider).getHandler(constraint))
                .map(failFunctionOnlyIfObligationElseFallBackToIdentity(isObligation)).collect(Collectors.toList());
    }

    private <U extends Message<?>> List<Supplier<Map<String, ?>>> constructAddMetaDataHandlersForConstraint(
            JsonNode constraint, boolean isObligation, U message) {
        return globalAddMetaDataProviders.stream()
                .filter(provider -> provider.isResponsible(constraint))
                .filter(provider -> provider.supports(message))
                .map(provider -> provider.getMetaDataSupplier(constraint))
                .map(failSupplierOnlyIfObligation(isObligation)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <R> List<Consumer<R>> constructResultConsumerHandlersForConstraint(JsonNode constraint,
                                                                               boolean isObligation, Class<R> handlerResult) {
        return globalConsumerProviders.stream().filter(provider -> provider.supports(handlerResult))
                .filter(provider -> provider.isResponsible(constraint))
                .map(provider -> ((ConsumerConstraintHandlerProvider<R>) provider).getHandler(constraint))
                .map(failConsumerOnlyIfObligation(isObligation)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <R> List<Function<R, R>> constructResultMappingHandlersForConstraint(JsonNode constraint,
                                                                                 boolean isObligation, Class<R> result) {
        return globalMappingProviders.stream().filter(provider -> provider.isResponsible(constraint))
                .filter(provider -> provider.supports(result))
                .map(provider -> ((MappingConstraintHandlerProvider<R>) provider).getHandler(constraint))
                .map(failFunctionOnlyIfObligationElseFallBackToIdentity(isObligation)).collect(Collectors.toList());
    }

    private List<Runnable> constructSimpleRunnableHandlersForConstraint(JsonNode constraint, boolean isObligation,
                                                                        AxonRunnableConstraintHandlerProvider.Signal signal) {
        return globalSimpleRunnableProviders.stream().filter(provider -> provider.isResponsible(constraint))

                .filter(provider -> (provider
                        .getSignal() == AxonRunnableConstraintHandlerProvider.Signal.PRE_AND_POST_HANDLE)
                        || (provider.getSignal() == signal))
                .map(provider -> provider.getHandler(constraint)).map(failRunnableOnlyIfObligation(isObligation))
                .collect(Collectors.toList());
    }

    private <U extends Message<?>> List<Method> collectAggregateConstraintHandlerMethods(Optional<List<Method>> constraintHandlerMethods,
                                                                                        JsonNode constraint, U message) {

        return constraintHandlerMethods.map(methods -> methods.stream()
                .filter((method) -> isMethodResponsibleForConstraintHandling(constraint, message, method))
                .collect(Collectors.toList())).orElse(Collections.emptyList());
    }

    private Function<Runnable, Runnable> failRunnableOnlyIfObligation(boolean isObligation) {
        return runnable -> () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                if (isObligation)
                    throw new AccessDeniedException("Failed to execute runnable constraint handler", t);
            }
        };
    }

    private <T> Function<Supplier<T>, Supplier<T>> failSupplierOnlyIfObligation(boolean isObligation) {
        return supplier -> () -> {
            try {
                return supplier.get();
            } catch (Throwable t) {
                if (isObligation)
                    throw new AccessDeniedException("Failed to execute supplier constraint handler", t);
                return null;
            }
        };
    }


    private <T> Function<Consumer<T>, Consumer<T>> failConsumerOnlyIfObligation(boolean isObligation) {
        return consumer -> value -> {
            try {
                consumer.accept(value);
            } catch (Throwable t) {
                if (isObligation)
                    throw new AccessDeniedException("Failed to execute consumer constraint handler", t);
            }
        };
    }

    private <T> Function<Function<T, T>, Function<T, T>> failFunctionOnlyIfObligationElseFallBackToIdentity(
            boolean isObligation) {
        return function -> value -> {
            try {
                return function.apply(value);
            } catch (Throwable t) {
                if (isObligation)
                    throw new AccessDeniedException("Failed to execute mapping constraint handler", t);
                return value;
            }
        };
    }

    private void failIfResourcePresent(AuthorizationDecision decision) {
        if (decision.getResource().isPresent())
            throw new AccessDeniedException(
                    "Decision attempted to modify " + "resource, which is not supported by this implementation.");
    }

}
