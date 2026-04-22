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
package io.sapl.spring.pep.constraints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.providers.AccessConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Constructs the enforcement plan P(d) for an authorization decision d,
 * following Algorithm 2 of the
 * enforcement framework.
 * <p>
 * Phase 1 resolves a handler for each obligation and advice in d by querying
 * the deployed handler providers.
 * A constraint is replaced by a synthetic failure runner attached to the
 * decision signal when the providers
 * return no handler, more than one handler, or a handler that is not
 * well-formed for the constraint's type.
 * <p>
 * Phase 2 sorts each per-signal sequence by ascending priority with the
 * handler-type tiebreak
 * Runner &lt; Mapper &lt; Consumer, then enforces the mapper-commutativity
 * invariant: every maximal run of
 * mappers at equal priority of length greater than one is replaced in place by
 * synthetic failure runners,
 * since the planner cannot prove commutativity of arbitrary mapper composition.
 */
@Slf4j
@RequiredArgsConstructor
public class EnforcementPlanner {

    private static final SignalType DECISION_SIGNAL_TYPE = new SignalType.ValueSignalType<>(Signal.DecisionSignal.class,
            AuthorizationDecision.class);

    private static final int SUBSTITUTE_PRIORITY = 0;

    private static final String ERROR_UNHANDLED_OBLIGATION = "Unhandled obligation (%s): %s";
    private static final String WARN_UNHANDLED_CONSTRAINT  = "Unhandled {} constraint ({}): {}";

    private final List<ConstraintHandlerProvider> providers;

    /**
     * Builds the enforcement plan P(d) for {@code decision} as specified by
     * Algorithm 2: handler resolution
     * (Phase 1) followed by sort and commutativity enforcement (Phase 2).
     *
     * @param decision the authorization decision whose obligations and advice are
     * to be planned
     * @param supportedSignals the set of signals the deployed PEP actually fires;
     * handlers attached to any
     * other signal are treated as not well-formed
     * @return the enforcement plan satisfying the ordering, type-signal
     * admissibility, mapper-tag, mapper
     * commutativity, supported signals, and coverage invariants
     */
    public EnforcementPlan plan(AuthorizationDecision decision, Set<SignalType> supportedSignals) {
        val entriesBySignal = resolveHandlerForEachConstraint(decision, supportedSignals);
        sortAndEnforceCommutativity(entriesBySignal);
        return new EnforcementPlan(entriesBySignal);
    }

    /**
     * Phase 1 of Algorithm 2: for every obligation and advice in {@code decision},
     * resolves a handler via the
     * registered providers and appends the resulting entry to its target signal's
     * sequence, or appends a
     * synthetic failure runner to the decision signal when resolution fails.
     */
    private Map<SignalType, List<EnforcementPlanEntry<?>>> resolveHandlerForEachConstraint(
            AuthorizationDecision decision, Set<SignalType> supportedSignals) {
        Map<SignalType, List<EnforcementPlanEntry<?>>> entriesBySignal = new HashMap<>();
        scheduleHandlersFor(decision.obligations(), ConstraintType.OBLIGATION, supportedSignals, entriesBySignal);
        scheduleHandlersFor(decision.advice(), ConstraintType.ADVICE, supportedSignals, entriesBySignal);
        return entriesBySignal;
    }

    /**
     * Phase 2 of Algorithm 2: sorts each per-signal sequence by the entry's natural
     * order (ascending priority
     * with handler-type tiebreak Runner &lt; Mapper &lt; Consumer) and replaces
     * every maximal same-priority
     * mapper run of length greater than one with synthetic failure runners.
     */
    private static void sortAndEnforceCommutativity(Map<SignalType, List<EnforcementPlanEntry<?>>> entriesBySignal) {
        for (val entries : entriesBySignal.values()) {
            entries.sort(null);
            replaceNonCommutingMapperGroups(entries);
        }
    }

    /**
     * Resolves a handler for each {@code constraint} in {@code constraints} (all
     * sharing
     * {@code constraintType}) and appends the resulting entry to the per-signal
     * sequence in
     * {@code entriesBySignal}.
     */
    private void scheduleHandlersFor(ArrayValue constraints, ConstraintType constraintType,
            Set<SignalType> supportedSignals, Map<SignalType, List<EnforcementPlanEntry<?>>> entriesBySignal) {
        for (val constraint : constraints) {
            val assignment = assignHandler(constraint, constraintType, supportedSignals);
            entriesBySignal.computeIfAbsent(assignment.signal(), signal -> new ArrayList<>()).add(assignment.entry());
        }
    }

    /**
     * Resolves the handler for one constraint by collecting all providers' results.
     * Returns the well-formed
     * scoped handler when exactly one provider claims the constraint and that
     * handler is admissible;
     * otherwise returns a failure substitute attached to the decision signal with
     * the matching reason
     * ({@link SubstitutionReason#UNRESOLVED}, {@link SubstitutionReason#AMBIGUOUS},
     * or
     * {@link SubstitutionReason#INADMISSIBLE}).
     */
    private Assignment assignHandler(Value constraint, ConstraintType constraintType,
            Set<SignalType> supportedSignals) {
        val matchingHandlers = providers.stream().map(provider -> provider.getConstraintHandler(constraint))
                .flatMap(Optional::stream).toList();

        if (matchingHandlers.isEmpty()) {
            return failureSubstitute(constraint, constraintType, SubstitutionReason.UNRESOLVED);
        }
        if (matchingHandlers.size() > 1) {
            return failureSubstitute(constraint, constraintType, SubstitutionReason.AMBIGUOUS);
        }
        val scopedHandler = matchingHandlers.getFirst();
        if (!isAdmissible(scopedHandler, constraintType, supportedSignals)) {
            return failureSubstitute(constraint, constraintType, SubstitutionReason.INADMISSIBLE);
        }
        return new Assignment(scopedHandler.signalType(),
                entry(scopedHandler.handler(), scopedHandler.priority(), constraintType, constraint));
    }

    /**
     * Returns true when {@code (a, s, p)} is well-formed for
     * {@code constraintType}, per the type-signal
     * admissibility invariant: the signal is in {@code supportedSignals}; advice
     * constraints carry no mapper;
     * mappers and consumers attach only to data-carrying (value) signals while
     * runners are admissible at any
     * signal.
     */
    private static boolean isAdmissible(ScopedConstraintHandler scopedHandler, ConstraintType constraintType,
            Set<SignalType> supportedSignals) {
        if (!supportedSignals.contains(scopedHandler.signalType())) {
            return false;
        }
        if (scopedHandler.handler() instanceof ConstraintHandler.Mapper<?>
                && constraintType != ConstraintType.OBLIGATION) {
            return false;
        }
        val dataCarrying = scopedHandler.signalType() instanceof SignalType.ValueSignalType<?>;
        return dataCarrying || scopedHandler.handler() instanceof ConstraintHandler.Runner;
    }

    /**
     * Walks {@code entries} and replaces every maximal run of mappers at equal
     * priority of length greater
     * than one with synthetic failure runners tagged
     * {@link SubstitutionReason#NON_COMMUTING_GROUP}. The
     * planner has no API for declaring pairwise commutativity, so any such group is
     * conservatively treated
     * as non-commuting.
     */
    private static void replaceNonCommutingMapperGroups(List<EnforcementPlanEntry<?>> entries) {
        var index = 0;
        while (index < entries.size()) {
            if (!isMapper(entries.get(index))) {
                index++;
                continue;
            }
            val groupPriority = entries.get(index).priority();
            var groupEnd      = index;
            while (groupEnd + 1 < entries.size() && isMapper(entries.get(groupEnd + 1))
                    && entries.get(groupEnd + 1).priority() == groupPriority) {
                groupEnd++;
            }
            if (groupEnd > index) {
                for (var i = index; i <= groupEnd; i++) {
                    entries.set(i, asNonCommutingSubstitute(entries.get(i)));
                }
            }
            index = groupEnd + 1;
        }
    }

    private static boolean isMapper(EnforcementPlanEntry<?> entry) {
        return entry.handler() instanceof ConstraintHandler.Mapper<?>;
    }

    /**
     * Builds the in-place substitute entry for a mapper that was part of a
     * non-commuting same-priority group,
     * preserving the original priority, constraint type, and constraint for
     * consistent failure reporting.
     */
    private static EnforcementPlanEntry<?> asNonCommutingSubstitute(EnforcementPlanEntry<?> original) {
        return entry(
                syntheticFailureRunner(original.constraint(), original.constraintType(),
                        SubstitutionReason.NON_COMMUTING_GROUP),
                original.priority(), original.constraintType(), original.constraint());
    }

    /**
     * Builds an assignment of a synthetic failure runner at the decision signal for
     * a constraint that could
     * not be resolved or admitted in Phase 1.
     */
    private Assignment failureSubstitute(Value constraint, ConstraintType constraintType, SubstitutionReason reason) {
        return new Assignment(DECISION_SIGNAL_TYPE, entry(syntheticFailureRunner(constraint, constraintType, reason),
                SUBSTITUTE_PRIORITY, constraintType, constraint));
    }

    /**
     * Returns the synthetic failure runner of the framework: on invocation it logs
     * the offending constraint;
     * if {@code constraintType} is obligation it additionally throws an
     * {@link AccessConstraintViolationException} to signal failure to the execution
     * algorithm; if
     * {@code constraintType} is advice it completes successfully, recording the
     * non-enforcement without
     * blocking the decision.
     */
    private static ConstraintHandler.Runner syntheticFailureRunner(Value constraint, ConstraintType constraintType,
            SubstitutionReason reason) {
        return () -> {
            log.warn(WARN_UNHANDLED_CONSTRAINT, constraintType, reason, constraint);
            if (constraintType == ConstraintType.OBLIGATION) {
                throw new AccessConstraintViolationException(ERROR_UNHANDLED_OBLIGATION.formatted(reason, constraint));
            }
        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static EnforcementPlanEntry<?> entry(ConstraintHandler<?> handler, int priority,
            ConstraintType constraintType, Value constraint) {
        return new EnforcementPlanEntry(handler, priority, constraintType, constraint);
    }

    /**
     * Result of resolving one constraint: the destination signal and the entry to
     * append there.
     */
    private record Assignment(SignalType signal, EnforcementPlanEntry<?> entry) {}

    /**
     * Why a constraint was replaced by a synthetic failure runner during planning.
     */
    enum SubstitutionReason {
        /** No provider returned a handler for the constraint. */
        UNRESOLVED,
        /** More than one provider returned a handler. */
        AMBIGUOUS,
        /**
         * A handler was returned but is not well-formed for the signal/constraint type.
         */
        INADMISSIBLE,
        /**
         * The mapper sat in a same-priority group whose commutativity cannot be
         * guaranteed.
         */
        NON_COMMUTING_GROUP
    }
}
