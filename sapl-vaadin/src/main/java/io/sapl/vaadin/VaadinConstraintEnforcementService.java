package io.sapl.vaadin;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * This class provides functions for constraint handling.
 * The function {@link VaadinConstraintEnforcementService#enforceConstraintsOfDecision(AuthorizationDecision, UI, VaadinPep)}
 * is used to execute all constraint handlers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaadinConstraintEnforcementService implements EnforceConstraintsOfDecision {
    private final List<VaadinFunctionConstraintHandlerProvider> globalVaadinFunctionProvider;
    private final List<ConsumerConstraintHandlerProvider<UI>> globalConsumerProviders;
    private final List<RunnableConstraintHandlerProvider> globalRunnableProviders;

    /**
     * This function adds a VaadinFunctionConstraintHandlerProvider to the list of global ConstraintHandlerProviders.
     * @param provider ConstraintHandlerProvider to be added
     */
    public void addGlobalVaadinFunctionProvider(VaadinFunctionConstraintHandlerProvider provider){
        this.globalVaadinFunctionProvider.add(provider);
    }

    /**
     * This function adds a UI ConsumerConstraintHandlerProvider to the list of global ConstraintHandlerProviders.
     * @param provider ConstraintHandlerProvider to be added
     */
    public void addGlobalConsumerProviders(ConsumerConstraintHandlerProvider<UI> provider){
        this.globalConsumerProviders.add(provider);
    }

    /**
     * This function adds a RunnableConstraintHandlerProvider to the list of global ConstraintHandlerProviders.
     * @param provider ConstraintHandlerProvider to be added
     */
    public void addGlobalRunnableProviders(RunnableConstraintHandlerProvider provider){
        this.globalRunnableProviders.add(provider);
    }

    /**
     * This function gets all constraint handlers, executes them and returns the derived decision.
     * In the following cases a DENY is returned:
     *   - No handlers have been executed
     *   - Runnable throws a non-fatal exception and obligations are present
     * @param authorizationDecision Current decision from subscription
     * @param ui Current Vaadin UI
     * @param vaadinPep vaadinPep containing additional local constraint handler
     * @return Derived decision as Mono
     */
    @Override
    public Mono<AuthorizationDecision> enforceConstraintsOfDecision(
            AuthorizationDecision authorizationDecision,
            UI ui,
            VaadinPep vaadinPep
    ){
        try {
            VaadinConstraintHandlerBundle handlerBundle = getHandlerBundleForDecision(authorizationDecision, vaadinPep);
            return executeConstraintHandlersAndDeriveFinalDecision(handlerBundle, authorizationDecision, ui);
        } catch (Throwable t) {
            Exceptions.throwIfFatal(t);
            log.error(t.getMessage());
            return Mono.just(AuthorizationDecision.DENY);
        }
    }

    /**
     * This function creates a new {@link VaadinConstraintHandlerBundle}, fills and returns it.
     * @param authorizationDecision Decision to get the obligations and advices from
     * @param vaadinPep vaadinPep containing additional local constraint handler
     * @return New {@link VaadinConstraintHandlerBundle}
     */
    private VaadinConstraintHandlerBundle getHandlerBundleForDecision(
            AuthorizationDecision authorizationDecision,
            VaadinPep vaadinPep
    ){
        VaadinConstraintHandlerBundle vaadinConstraintHandlerBundle = new VaadinConstraintHandlerBundle();

        authorizationDecision.getObligations().ifPresent((obligations) -> {
            for (JsonNode obligation : obligations) {
                addConstraintHandlerToHandlerBundle(obligation, vaadinConstraintHandlerBundle, true, vaadinPep);
            }
        });

        authorizationDecision.getAdvice().ifPresent((advices) -> {
            for (JsonNode advice: advices) {
                addConstraintHandlerToHandlerBundle(advice, vaadinConstraintHandlerBundle, false, vaadinPep);
            }
        });

        return vaadinConstraintHandlerBundle;
    }

    /**
     * This function creates the runnable handlers, consumer handlers and vaadin function handlers and adds them to the given handler bundle.
     * @param obligation Current Obligation or Advice
     * @param handlerBundle Handler bundle to add the handlers
     * @param isObligation Set to true if param obligation is an obligation or to false if param obligation is an advice.
     * @param vaadinPep vaadinPep containing additional local constraint handler
     */
    private void addConstraintHandlerToHandlerBundle(
            JsonNode obligation,
            VaadinConstraintHandlerBundle handlerBundle,
            boolean isObligation,
            VaadinPep vaadinPep
    ){
        var runnableHandler = constructRunnableHandlersForConstraint(obligation, isObligation, vaadinPep);
        var consumerHandler = constructConsumerHandlersForConstraint(obligation, isObligation, vaadinPep);
        var vaadinFunctionHandler = constructVaadinFunctionHandlersForConstraint(obligation, isObligation, vaadinPep);
        if ( runnableHandler.size() + consumerHandler.size() + vaadinFunctionHandler.size() == 0 ){
            throw new AccessDeniedException(String.format("No handler found for obligation: %s", obligation.asText()));
        }
        handlerBundle.runnableHandlerList.addAll(runnableHandler);
        handlerBundle.consumerHandlerList.addAll(consumerHandler);
        handlerBundle.vaadinFunctionHandlerList.addAll(vaadinFunctionHandler);
    }

    /**
     * This function creates and returns the vaadin function handlers.
     * @param constraint Obligation or Advice
     * @param isObligation Set to true if param obligation is an obligation or to false if param obligation is an advice.
     * @param vaadinPep vaadinPep containing additional local constraint handler
     */
    private List<Function<UI, Mono<Boolean>>> constructVaadinFunctionHandlersForConstraint(JsonNode constraint,
                                                                                           boolean isObligation,
                                                                                           VaadinPep vaadinPep){
        return Stream.concat(
                globalVaadinFunctionProvider.stream(),
                vaadinPep.getLocalVaadinFunctionProvider().stream())
                .filter(provider -> provider.isResponsible(constraint))
                .map(provider -> provider.getHandler(constraint))
                .map(failVaadinFunctionHandlerOnlyIfObligationOrFatal(isObligation))
                .collect(Collectors.toList());
    }

    /**
     * This function creates and returns the consumer handlers.
     * @param constraint Obligation or Advice
     * @param isObligation Set to true if param obligation is an obligation or to false if param obligation is an advice.
     * @param vaadinPep vaadinPep containing additional local constraint handler
     */
    private List<Consumer<UI>> constructConsumerHandlersForConstraint(JsonNode constraint,
                                                                      boolean isObligation,
                                                                      VaadinPep vaadinPep){
        return Stream.concat(
                globalConsumerProviders.stream(),
                vaadinPep.getLocalConsumerProviders().stream())
                .filter(provider -> provider.isResponsible(constraint))
                .map(provider -> provider.getHandler(constraint))
                .map(failConsumerHandlerOnlyIfObligationOrFatal(isObligation))
                .collect(Collectors.toList());
    }

    /**
     * This function creates and returns the runnable handlers.
     * @param constraint Obligation or Advice
     * @param isObligation Set to true if param obligation is an obligation or to false if param obligation is an advice.
     * @param vaadinPep vaadinPep containing additional local constraint handler
     */
    private List<Runnable> constructRunnableHandlersForConstraint(JsonNode constraint,
                                                                  boolean isObligation,
                                                                  VaadinPep vaadinPep){
        return Stream.concat(
                globalRunnableProviders.stream(),
                vaadinPep.getLocalRunnableProviders().stream())
                .filter(provider -> provider.isResponsible(constraint))
                .map(provider -> provider.getHandler(constraint))
                .map(failRunnableHandlerOnlyIfObligationOrFatal(isObligation))
                .collect(Collectors.toList());
    }

    /**
     * This function creates and returns a wrapped vaadin function to handle throw ables.
     * The wrapped function behaves as follows:
     *   - Original function is executed
     *   - If a fatal throwable is caught, it is rethrown.
     *   - If a non-fatal throwable is caught and isObligation is set, an {@link AccessDeniedException} is thrown.
     *   - If a non-fatal throwable is caught and isObligation is not set, a {@link Mono#just(Object)} with FALSE is returned.
     * @param isObligation Set to true, if executed in context of an obligation, false otherwise.
     * @return Wrapped function
     */
    private Function<Function<UI, Mono<Boolean>>, Function<UI, Mono<Boolean>>> failVaadinFunctionHandlerOnlyIfObligationOrFatal(boolean isObligation) {
        return handler -> ui -> {
            try {
                return handler.apply(ui);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                if (isObligation) {
                    throw new AccessDeniedException("Failed to execute VaadinConstraintHandler", e);
                }
                return Mono.just(Boolean.FALSE);
            }
        };
    }

    /**
     * This function creates and returns a wrapped consumer to handle throw ables.
     * The wrapped function behaves as follows:
     *   - Consumer is executed
     *   - If a fatal throwable is caught, it is rethrown.
     *   - If a non-fatal throwable is caught and isObligation is set, an {@link AccessDeniedException} is thrown.
     * @param isObligation Set to true, if executed in context of an obligation, false otherwise.
     * @return Wrapped function
     */
    private Function<Consumer<UI>, Consumer<UI>> failConsumerHandlerOnlyIfObligationOrFatal(boolean isObligation) {
        return handler -> ui -> {
            try {
                handler.accept(ui);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                if (isObligation) {
                    throw new AccessDeniedException("Failed to execute VaadinConstraintHandler", e);
                }
            }
        };
    }

    /**
     * This function creates and returns a wrapped runnable to handle throw ables.
     * The wrapped function behaves as follows:
     *   - Runnable is executed
     *   - If a fatal throwable is caught, it is rethrown.
     *   - If a non-fatal throwable is caught and isObligation is set, an {@link AccessDeniedException} is thrown.
     * @param isObligation Set to true, if executed in context of an obligation, false otherwise.
     * @return Wrapped function
     */
    private Function<Runnable, Runnable> failRunnableHandlerOnlyIfObligationOrFatal(boolean isObligation) {
        return handler -> () -> {
            try {
                handler.run();
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                if (isObligation) {
                    throw new AccessDeniedException("Failed to execute VaadinConstraintHandler", e);
                }
            }
        };
    }

    /**
     * This function executes all constraint handlers and returns the final derived decision.
     * @param handlerBundle Handler bundle containing all constraint handlers to execute
     * @param authorizationDecision Decision passed to Vaadin function handlers
     * @param ui Current Vaadin UI
     * @return Final decision from Vaadin function handlers
     */
    private Mono<AuthorizationDecision> executeConstraintHandlersAndDeriveFinalDecision(
            VaadinConstraintHandlerBundle handlerBundle,
            AuthorizationDecision authorizationDecision,
            UI ui){
        executeHandler(handlerBundle.runnableHandlerList);
        executeHandler(handlerBundle.consumerHandlerList, ui);
        return handlerBundle.vaadinFunctionHandlerList.isEmpty() ?
                Mono.just(authorizationDecision) :
                executeHandler(handlerBundle.vaadinFunctionHandlerList, authorizationDecision, ui);
    }

    /**
     * This function executes every runnable in the handlers list.
     * @param handlers List containing the runnables to be executed
     */
    private void executeHandler(Iterable<Runnable> handlers){
        for (Runnable handler : handlers) {
            handler.run();
        }
    }

    /**
     * This function executes every consumer in the handlers list.
     * @param handlers List containing the consumers to be executed
     * @param ui Current Vaadin UI, passed to consumers
     */
    private void executeHandler(Iterable<Consumer<UI>> handlers, UI ui){
        for (Consumer<UI> handler : handlers) {
            handler.accept(ui);
        }
    }

    /**
     * This function executes every function in the handlers list and returns the final derived decision
     * @param handlers List containing the consumers to be executed
     * @param authorizationDecision Current decision, passed to functions
     * @param ui Current Vaadin UI, passed to functions
     * @return Final derived decision
     */
    private Mono<AuthorizationDecision> executeHandler(Collection<Function<UI, Mono<Boolean>>> handlers,
                                                       AuthorizationDecision authorizationDecision,
                                                       UI ui){
        // get responses from constraintHandlers
        List<Mono<Boolean>> decisionPublishers = handlers
                .stream()
                .map((handler)->handler.apply(ui))
                .collect(Collectors.toList());

        return Mono.zip(
                        decisionPublishers,
                        arr -> {
                            boolean allSuccessful = true;
                            for (Object result : arr) {
                                if (!(Boolean) result) {
                                    allSuccessful = false;
                                    break;
                                }
                            }
                            if ( allSuccessful ){
                                return authorizationDecision;
                            } else {
                                return AuthorizationDecision.DENY;
                            }
                        })
                .doOnError(throwable -> log.error("Error when applying constraints"))
                .onErrorReturn(AuthorizationDecision.DENY);
    }
}
