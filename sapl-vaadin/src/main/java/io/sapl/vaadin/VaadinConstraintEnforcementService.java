/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.vaadin;

import static reactor.core.Exceptions.throwIfFatal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.vaadin.flow.component.UI;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ConstraintType;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinSignals;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces the constraints of an authorization decision for a Vaadin policy
 * enforcement point.
 * <p>
 * Each decision is turned into an {@link EnforcementPlan} by an
 * {@link EnforcementPlanner} that knows the global
 * {@link ConstraintHandlerProvider} beans, the providers registered on the
 * {@link VaadinPep} and the {@link VaadinFunctionConstraintHandlerProvider}s.
 * The plan is executed for the decision signal and for the UI signal, see
 * {@link VaadinSignals}. Afterwards the Vaadin functions, which may wait for
 * user interaction, are executed. The decision is returned unchanged when
 * every obligation handler succeeded and every Vaadin function agreed. In all
 * other cases {@link AuthorizationDecision#DENY} is returned: an obligation
 * without handler, an obligation claimed by more than one provider, a failing
 * obligation handler, a Vaadin function returning {@code false} and a decision
 * carrying a replacement resource, which a UI cannot honour.
 */
@Slf4j
public class VaadinConstraintEnforcementService implements EnforceConstraintsOfDecision {

    private static final int DEFAULT_PRIORITY = 0;

    private static final String ERROR_PROVIDER_RETURNED_NO_HANDLER = "The Vaadin function constraint handler provider returned no handler.";
    private static final String WARN_ADVICE_FUNCTION_FAILED        = "Vaadin function for advice {} failed and is ignored: {}";
    private static final String WARN_ENFORCEMENT_FAILED            = "Constraint enforcement failed, access is denied: {}";
    private static final String WARN_OBLIGATION_FUNCTION_FAILED    = "Vaadin function for obligation {} failed, access is denied: {}";

    private final List<ConstraintHandlerProvider>               globalConstraintHandlerProviders;
    private final List<VaadinFunctionConstraintHandlerProvider> globalVaadinFunctionProviders;
    private final ObjectMapper                                  objectMapper;

    /**
     * Creates the service with explicit provider lists.
     *
     * @param constraintHandlerProviders providers claiming decision and UI signal
     * handlers
     * @param vaadinFunctionProviders providers of Vaadin functions
     * @param objectMapper mapper used by the enforcement planner
     */
    public VaadinConstraintEnforcementService(List<ConstraintHandlerProvider> constraintHandlerProviders,
            List<VaadinFunctionConstraintHandlerProvider> vaadinFunctionProviders,
            ObjectMapper objectMapper) {
        this.globalConstraintHandlerProviders = new ArrayList<>(constraintHandlerProviders);
        this.globalVaadinFunctionProviders    = new ArrayList<>(vaadinFunctionProviders);
        this.objectMapper                     = objectMapper;
    }

    /**
     * Adds a provider consulted for every decision of every Vaadin policy
     * enforcement point.
     *
     * @param provider provider to add
     */
    public void addGlobalConstraintHandlerProvider(ConstraintHandlerProvider provider) {
        globalConstraintHandlerProviders.add(provider);
    }

    /**
     * Adds a Vaadin function provider consulted for every decision of every
     * Vaadin policy enforcement point.
     *
     * @param provider provider to add
     */
    public void addGlobalVaadinFunctionProvider(VaadinFunctionConstraintHandlerProvider provider) {
        globalVaadinFunctionProviders.add(provider);
    }

    @Override
    public Mono<AuthorizationDecision> enforceConstraintsOfDecision(AuthorizationDecision authorizationDecision, UI ui,
            VaadinPep vaadinPep) {
        try {
            val plan   = planFor(authorizationDecision, vaadinPep);
            var failed = plan.execute(DecisionSignal.of(authorizationDecision), false).failureState();
            failed = plan.execute(OutputSignal.of(UI.class, ui), failed).failureState();
            if (failed) {
                return Mono.just(AuthorizationDecision.DENY);
            }
            return executeVaadinFunctions(plan, authorizationDecision, ui);
        } catch (Throwable t) {
            throwIfFatal(t);
            log.atWarn().addArgument(t::toString).log(WARN_ENFORCEMENT_FAILED);
            return Mono.just(AuthorizationDecision.DENY);
        }
    }

    private EnforcementPlan planFor(AuthorizationDecision decision, VaadinPep vaadinPep) {
        val providers = new ArrayList<>(globalConstraintHandlerProviders);
        providers.addAll(vaadinPep.getLocalConstraintHandlerProviders());
        val functionProviders = new ArrayList<>(globalVaadinFunctionProviders);
        functionProviders.addAll(vaadinPep.getLocalVaadinFunctionProvider());
        providers.add(new VaadinFunctionProviders(functionProviders));
        return new EnforcementPlanner(providers, objectMapper).plan(decision, VaadinSignals.SUPPORTED_SIGNALS);
    }

    private static Mono<AuthorizationDecision> executeVaadinFunctions(EnforcementPlan plan,
            AuthorizationDecision decision, UI ui) {
        val agreements = new ArrayList<Mono<Boolean>>();
        for (val entry : plan.entriesFor(DecisionSignal.SIGNAL_TYPE)) {
            if (entry.handler() instanceof ScheduledVaadinFunction(var function) && function != null) {
                agreements.add(agreementOf(function, ui, entry.constraintType(), entry.constraint()));
            }
        }
        if (agreements.isEmpty()) {
            return Mono.just(decision);
        }
        return Mono.zip(agreements, VaadinConstraintEnforcementService::allAgreed)
                .map(agreed -> Boolean.TRUE.equals(agreed) ? decision : AuthorizationDecision.DENY);
    }

    private static Mono<Boolean> agreementOf(Function<UI, Mono<Boolean>> function, UI ui, ConstraintType constraintType,
            Value constraint) {
        val           isObligation = constraintType == ConstraintType.OBLIGATION;
        Mono<Boolean> agreement;
        try {
            agreement = function.apply(ui);
        } catch (Throwable t) {
            throwIfFatal(t);
            return Mono.just(logFailure(isObligation, constraint, t));
        }
        if (agreement == null) {
            return Mono.just(logFailure(isObligation, constraint,
                    new IllegalStateException(ERROR_PROVIDER_RETURNED_NO_HANDLER)));
        }
        return agreement.defaultIfEmpty(Boolean.FALSE)
                .onErrorResume(t -> Mono.just(logFailure(isObligation, constraint, t)));
    }

    private static Boolean logFailure(boolean isObligation, Value constraint, Throwable t) {
        if (isObligation) {
            log.atWarn().addArgument(constraint).addArgument(t::toString).log(WARN_OBLIGATION_FUNCTION_FAILED);
            return Boolean.FALSE;
        }
        log.atWarn().addArgument(constraint).addArgument(t::toString).log(WARN_ADVICE_FUNCTION_FAILED);
        return Boolean.TRUE;
    }

    private static Boolean allAgreed(Object[] agreements) {
        for (val agreement : agreements) {
            if (!Boolean.TRUE.equals(agreement)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    /**
     * Presents the Vaadin function providers to the planner as one provider, so
     * the planner resolves the claims and the obligation or advice nature of each
     * constraint. The handler scheduled on the decision signal only records the
     * function, the function itself runs after the synchronous signals.
     */
    private record VaadinFunctionProviders(List<VaadinFunctionConstraintHandlerProvider> providers)
            implements ConstraintHandlerProvider {

        @Override
        public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
            val handlers = new ArrayList<ScopedConstraintHandler>();
            for (val provider : providers) {
                if (provider.isResponsible(constraint)) {
                    handlers.add(
                            new ScopedConstraintHandler(new ScheduledVaadinFunction(provider.getHandler(constraint)),
                                    DecisionSignal.SIGNAL_TYPE, DEFAULT_PRIORITY));
                }
            }
            return handlers;
        }
    }

    private record ScheduledVaadinFunction(@Nullable Function<UI, Mono<Boolean>> function) implements Runner {

        @Override
        public void run() {
            if (function == null) {
                throw new IllegalStateException(ERROR_PROVIDER_RETURNED_NO_HANDLER);
            }
        }
    }

}
