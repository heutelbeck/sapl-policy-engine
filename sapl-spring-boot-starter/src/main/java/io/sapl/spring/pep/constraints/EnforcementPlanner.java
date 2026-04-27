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
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.ObjectMapper;

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
 * <p>
 * SAPL-specific extension: when {@code decision.resource()} is not
 * {@link UndefinedValue}, the planner
 * synthesises an implicit obligation-tagged Mapper at the OutputSignal
 * supported by the PEP, with priority
 * {@link Integer#MIN_VALUE}. The mapper ignores the RAP's output and returns
 * the resource value unmarshalled
 * to the OutputSignal's value type. If no OutputSignal is in
 * {@code supportedSignals}, the implicit
 * obligation is replaced by an {@link SubstitutionReason#INADMISSIBLE} failure
 * substitute at the decision
 * signal. Conversion failures at runtime fail the obligation through the
 * executor's standard catch path.
 */
@Slf4j
@RequiredArgsConstructor
public class EnforcementPlanner {

    private static final SignalType DECISION_SIGNAL_TYPE = Signal.DecisionSignal.TYPE;

    private static final int SUBSTITUTE_PRIORITY = 0;

    private static final String ERROR_CANNOT_MAP_RESOURCE  = "Cannot map resource %s to %s";
    private static final String ERROR_UNHANDLED_OBLIGATION = "Unhandled obligation (%s): %s";
    private static final String WARN_UNHANDLED_CONSTRAINT  = "Unhandled {} constraint ({}): {}";

    private final List<ConstraintHandlerProvider> providers;
    private final ObjectMapper                    objectMapper;

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
        val entriesBySignal  = resolveHandlerForEachConstraint(decision, supportedSignals);
        val outputSignalType = findOutputSignalType(supportedSignals);
        addImplicitResourceObligationIfPresent(decision, outputSignalType, entriesBySignal);
        sortAndEnforceCommutativity(entriesBySignal);
        val outputType = outputSignalType == null ? null : outputSignalType.valueType();
        return new EnforcementPlan(outputType, entriesBySignal);
    }

    private static @Nullable ValueSignalType<?> findOutputSignalType(Set<SignalType> supportedSignals) {
        for (val signal : supportedSignals) {
            if (signal instanceof ValueSignalType<?> v && Signal.OutputSignal.class.equals(v.type())) {
                return v;
            }
        }
        return null;
    }

    /**
     * SAPL-specific step: when {@code decision.resource()} is not
     * {@link UndefinedValue}, attaches an implicit
     * obligation-tagged Mapper at the supported OutputSignal at priority
     * {@link Integer#MIN_VALUE} that
     * substitutes the RAP's output with the resource value unmarshalled to the
     * OutputSignal's value type.
     * Falls back to an {@link SubstitutionReason#INADMISSIBLE} substitute at the
     * decision signal when no
     * OutputSignal is supported.
     */
    private void addImplicitResourceObligationIfPresent(AuthorizationDecision decision,
            @Nullable ValueSignalType<?> outputSignal, Map<SignalType, List<EnforcementPlanEntry<?>>> entriesBySignal) {
        if (decision.resource() instanceof UndefinedValue) {
            return;
        }
        if (outputSignal == null) {
            val substitute = failureSubstitute(decision.resource(), ConstraintType.OBLIGATION,
                    SubstitutionReason.INADMISSIBLE);
            entriesBySignal.computeIfAbsent(substitute.signal(), signal -> new ArrayList<>()).add(substitute.entry());
            return;
        }
        val outputType     = outputSignal.valueType().resolve(Object.class);
        val resourceMapper = resourceSubstitutionMapper(decision.resource(), outputType);
        entriesBySignal.computeIfAbsent(outputSignal, signal -> new ArrayList<>())
                .add(entry(resourceMapper, Integer.MIN_VALUE, ConstraintType.OBLIGATION, decision.resource()));
    }

    private Mapper<Object> resourceSubstitutionMapper(Value resource, Class<?> targetType) {
        return ignored -> {
            try {
                return objectMapper.readValue(ValueJsonMarshaller.toJsonString(resource), targetType);
            } catch (Exception exception) {
                throw new AccessDeniedException(
                        ERROR_CANNOT_MAP_RESOURCE.formatted(resource, targetType.getSimpleName()), exception);
            }
        };
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
            for (val assignment : assignHandlers(constraint, constraintType, supportedSignals)) {
                entriesBySignal.computeIfAbsent(assignment.signal(), signal -> new ArrayList<>())
                        .add(assignment.entry());
            }
        }
    }

    /**
     * Resolves all handlers for one constraint by collecting providers' results.
     * Exactly one provider must claim the constraint (return a non-empty list).
     * That provider may return one or more handlers, each scoped to its own
     * signal and priority. Each well-formed handler is scheduled independently;
     * any inadmissible handler in the bundle fails the entire claim.
     * <p>
     * Returns a singleton failure substitute attached to the decision signal
     * when no provider claims, multiple providers claim, or any returned handler
     * is not admissible
     * ({@link SubstitutionReason#UNRESOLVED}, {@link SubstitutionReason#AMBIGUOUS},
     * or {@link SubstitutionReason#INADMISSIBLE}).
     */
    private List<Assignment> assignHandlers(Value constraint, ConstraintType constraintType,
            Set<SignalType> supportedSignals) {
        val claims = providers.stream().map(provider -> provider.getConstraintHandlers(constraint, supportedSignals))
                .filter(claim -> !claim.isEmpty()).toList();

        if (claims.isEmpty()) {
            return List.of(failureSubstitute(constraint, constraintType, SubstitutionReason.UNRESOLVED));
        }
        if (claims.size() > 1) {
            return List.of(failureSubstitute(constraint, constraintType, SubstitutionReason.AMBIGUOUS));
        }
        val scopedHandlers = claims.getFirst();
        for (val scopedHandler : scopedHandlers) {
            if (!isAdmissible(scopedHandler, constraintType, supportedSignals)) {
                return List.of(failureSubstitute(constraint, constraintType, SubstitutionReason.INADMISSIBLE));
            }
        }
        val assignments = new ArrayList<Assignment>(scopedHandlers.size());
        for (val scopedHandler : scopedHandlers) {
            assignments.add(new Assignment(scopedHandler.signalType(),
                    entry(scopedHandler.handler(), scopedHandler.priority(), constraintType, constraint)));
        }
        return assignments;
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
        if (scopedHandler.handler() instanceof Mapper<?> && constraintType != ConstraintType.OBLIGATION) {
            return false;
        }
        val dataCarrying = scopedHandler.signalType() instanceof ValueSignalType<?>;
        return dataCarrying || scopedHandler.handler() instanceof Runner;
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
        return entry.handler() instanceof Mapper<?>;
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
     * {@link AccessDeniedException} to signal failure to the execution
     * algorithm; if
     * {@code constraintType} is advice it completes successfully, recording the
     * non-enforcement without
     * blocking the decision.
     */
    private static Runner syntheticFailureRunner(Value constraint, ConstraintType constraintType,
            SubstitutionReason reason) {
        return () -> {
            log.warn(WARN_UNHANDLED_CONSTRAINT, constraintType, reason, constraint);
            if (constraintType == ConstraintType.OBLIGATION) {
                throw new AccessDeniedException(ERROR_UNHANDLED_OBLIGATION.formatted(reason, constraint));
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
