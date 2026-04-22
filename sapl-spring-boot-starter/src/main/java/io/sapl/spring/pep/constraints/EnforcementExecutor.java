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

import io.sapl.spring.pep.constraints.ConstraintHandler.Consumer;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.util.Maybe;
import io.sapl.spring.util.Maybe.Absent;
import io.sapl.spring.util.Maybe.Present;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Exceptions;

/**
 * Executes the entries of an {@link EnforcementPlan} for a single signal in
 * plan order, applying mappers,
 * consumers, and runners. Continues past failing handlers (best-effort
 * discharge); failures of
 * obligation-tagged entries set the returned failure state.
 */
@Slf4j
public class EnforcementExecutor {

    private static final String WARN_HANDLER_FAILED = "Constraint handler failed at signal {} for {} constraint {}: {}";

    /**
     * Executes the plan entries scheduled for {@code signal} in plan order,
     * applying mappers, consumers, and
     * runners best-effort: a handler that throws is logged and skipped; only
     * obligation-tagged failures flip
     * the returned failure state. JVM-fatal and Reactor-fatal throwables are
     * re-raised via
     * {@link Exceptions#throwIfFatal}.
     *
     * @param plan the enforcement plan whose entries for {@code signal} are to be
     * executed
     * @param signal the fired signal; data-carrying signals contribute their value
     * as the initial
     * current value, self-contained signals start with {@link Maybe.Absent}
     * @param priorFailureState failure state propagated from earlier signals; once
     * {@code true} it remains
     * {@code true} for the rest of the run
     * @return the (possibly transformed) value carried through the entries together
     * with the updated failure
     * state
     */
    public EnforcementResult<?> execute(EnforcementPlan plan, Signal signal, boolean priorFailureState) {
        val           entries      = plan.entriesFor(signal.type());
        Maybe<Object> currentValue = switch (signal) {
                                   case Signal.ValueSignal<?> valueSignal -> Maybe.<Object>of(valueSignal.value());
                                   case Signal.VoidSignal ignored         -> Maybe.absent();
                                   };
        var           failureState = priorFailureState;

        for (val entry : entries) {
            try {
                currentValue = apply(entry.handler(), currentValue);
            } catch (Throwable throwable) {
                Exceptions.throwIfFatal(throwable);
                log.warn(WARN_HANDLER_FAILED, signal.type(), entry.constraintType(), entry.constraint(),
                        throwable.toString());
                if (entry.constraintType() == ConstraintType.OBLIGATION) {
                    failureState = true;
                }
            }
        }
        return new EnforcementResult<>(currentValue, failureState);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Maybe<Object> apply(ConstraintHandler<?> handler, Maybe<Object> currentValue) {
        return switch (handler) {
        case Runner runner        -> {
            runner.run();
            yield currentValue;
        }
        case Consumer<?> consumer -> switch (currentValue) {
                              case Present<Object>(var presentValue)     -> {
                                  ((Consumer) consumer).accept(presentValue);
                                  yield currentValue;
                              }
                              case Absent<Object> ignored                -> currentValue;
                              };
        case Mapper<?> mapper     -> switch (currentValue) {
                              case Present<Object>(var presentValue)     ->
                                  Maybe.of(((Mapper) mapper).apply(presentValue));
                              case Absent<Object> ignored                -> currentValue;
                              };
        };
    }
}
