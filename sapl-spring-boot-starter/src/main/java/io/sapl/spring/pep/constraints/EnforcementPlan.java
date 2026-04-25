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

import java.util.List;
import java.util.Map;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.ConstraintHandler.Consumer;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.Signal.AfterTerminationSignal;
import io.sapl.spring.pep.constraints.Signal.CancelSignal;
import io.sapl.spring.pep.constraints.Signal.CompleteSignal;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.Signal.SubscriptionSignal;
import io.sapl.spring.pep.constraints.Signal.TerminationSignal;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import io.sapl.spring.util.Maybe;
import io.sapl.spring.util.Maybe.Absent;
import io.sapl.spring.util.Maybe.Present;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.ResolvableType;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * Per-decision schedule mapping each {@link SignalType} to the ordered sequence
 * of {@link EnforcementPlanEntry} instances to discharge when that signal
 * fires. Implements Algorithm 3 of the enforcement framework: when a signal
 * fires, the entries scheduled for it are applied in plan order, mappers
 * threading the carried value, consumers observing it, runners ignoring it.
 */
@Slf4j
public record EnforcementPlan(
        @Nullable ResolvableType outputType,
        Map<SignalType, List<EnforcementPlanEntry<?>>> entries) {

    private static final String ERROR_ACCESS_DENIED_AT_SIGNAL = "Access Denied. An obligation handler failed during %s enforcement.";
    private static final String ERROR_ACCESS_DENIED_OBLIGATION_FAILED = "Access Denied. An obligation handler failed during error-signal enforcement.";
    private static final String ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED = "Access Denied. A post-invocation obligation handler failed after the protected method had already executed. Side effects of the invocation may have occurred.";
    private static final String ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED = "Access Denied. A pre-invocation obligation handler failed. The protected method was not invoked.";
    private static final String WARN_HANDLER_FAILED = "Constraint handler failed at signal {} for {} constraint {}: {}";

    /**
     * Convenience constructor for plans without a registered OutputSignal type.
     * Calling {@link #enforceOutputConstraints(Object, boolean)} on such a plan
     * passes the value through unchanged.
     */
    public EnforcementPlan(Map<SignalType, List<EnforcementPlanEntry<?>>> entries) {
        this(null, entries);
    }

    /**
     * Returns the ordered sequence of entries scheduled for {@code signalType}, or
     * an empty list when no
     * entries are scheduled at that signal.
     */
    public List<EnforcementPlanEntry<?>> entriesFor(SignalType signalType) {
        return entries.getOrDefault(signalType, List.of());
    }

    /**
     * Discharges the entries scheduled for {@code signal} in plan order,
     * applying mappers, consumers, and runners best-effort: a handler that throws
     * is logged and skipped; only obligation-tagged failures flip the returned
     * failure state. JVM-fatal and Reactor-fatal throwables are re-raised via
     * {@link Exceptions#throwIfFatal}.
     * </p>
     *
     * @param signal the fired signal; data-carrying signals contribute their value
     * as the initial current value, self-contained signals start with
     * {@link Maybe.Absent}
     * @param priorFailureState failure state propagated from earlier signals; once
     * {@code true} it remains {@code true} for the rest of the run
     * @return the (possibly transformed) value carried through the entries together
     * with the updated failure state
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public EnforcementResult<?> execute(Signal signal, boolean priorFailureState) {
        val           signalEntries = entriesFor(signal.type());
        Maybe<Object> currentValue  = switch (signal) {
                                    case Signal.OutputSignal<?> output     -> (Maybe) output.maybeValue();
                                    case Signal.ValueSignal<?> valueSignal -> Maybe.<Object>of(valueSignal.value());
                                    case Signal.VoidSignal ignored         -> Maybe.absent();
                                    };
        var           failureState  = priorFailureState;

        for (val entry : signalEntries) {
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

    /**
     * Reactive error-path entry point: fires {@link ErrorSignal} for {@code t}
     * and returns the resolved error wrapped in {@link Mono#error(Throwable)}.
     * Suitable as a method reference for
     * {@code .onErrorResume(plan::enforceErrorConstraints)}
     * on both Mono and Flux pipelines (since {@code Mono} is a {@code Publisher}).
     */
    public Mono<Object> enforceErrorConstraints(Throwable t) {
        return Mono.error(enforceErrorConstraintsAsThrowable(t));
    }

    /**
     * Blocking error-path entry point: fires {@link ErrorSignal} for {@code t}
     * and returns the resolved {@link Throwable} for the caller to {@code throw}.
     * </p>
     * Resolution rules: a fatal Throwable is re-raised immediately; a failure of
     * the error-signal obligation itself escalates to a fresh
     * {@link AccessDeniedException}; a Mapper that returned a Throwable replaces
     * {@code t}; otherwise {@code t} passes through unchanged.
     */
    public Throwable enforceErrorConstraintsAsThrowable(Throwable t) {
        Exceptions.throwIfFatal(t);
        EnforcementResult<?> errorResult;
        try {
            errorResult = execute(ErrorSignal.of(t), false);
        } catch (Throwable handlerFailure) {
            Exceptions.throwIfFatal(handlerFailure);
            return handlerFailure;
        }
        if (errorResult.failureState()) {
            return new AccessDeniedException(ERROR_ACCESS_DENIED_OBLIGATION_FAILED);
        }
        if (errorResult.value() instanceof Present<?>(var v) && v instanceof Throwable mapped) {
            return mapped;
        }
        return t;
    }

    /**
     * Pre-invocation entry point: fires {@link DecisionSignal} carrying the
     * authorization decision and then, regardless of any failure at the decision
     * signal, fires {@link InputSignal} carrying the method invocation (Mapper
     * handlers may mutate the invocation in place). Both signals always run so
     * policies can react at the decision and the input level. Throws
     * {@link AccessDeniedException} after both have fired if any obligation
     * handler failed.
     */
    public void enforcePreInvocationConstraints(AuthorizationDecision decision, MethodInvocation invocation) {
        var failed = execute(DecisionSignal.of(decision), false).failureState();
        failed = execute(InputSignal.of(invocation), failed).failureState();
        if (failed) {
            throw new AccessDeniedException(ERROR_ACCESS_DENIED_PRE_INVOCATION_OBLIGATION_FAILED);
        }
    }

    /**
     * Post-RAP decision entry point: fires {@link DecisionSignal} carrying the
     * authorization decision and returns the failure state of its handlers
     * without throwing, for the caller to thread into the subsequent
     * {@link OutputSignal} call. Used by PostEnforce flows where decision-time
     * obligation failures must propagate into the output signal rather than
     * abort early.
     */
    public boolean enforceDecisionConstraints(AuthorizationDecision decision) {
        return execute(DecisionSignal.of(decision), false).failureState();
    }

    /**
     * Output entry point: fires {@link OutputSignal} carrying {@code value} typed
     * by this plan's output type, threading {@code priorFailure} into the
     * execution. For void or {@link Void} output types the signal fires with no
     * value (Mappers and Consumers are skipped, Runners still fire). When this
     * plan was built without a registered OutputSignal type the call behaves
     * like an empty entry list: no signal fires, the value passes through, but
     * {@code priorFailure} still triggers an {@link AccessDeniedException} so
     * upstream failure is not silently dropped. Returns the (possibly
     * Mapper-transformed) value, or {@code null} for empty results.
     */
    public @Nullable Object enforceOutputConstraints(@Nullable Object value, boolean priorFailure) {
        if (outputType == null) {
            if (priorFailure) {
                throw new AccessDeniedException(ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED);
            }
            return value;
        }
        val signal = isVoidReturn() ? OutputSignal.empty(outputType) : OutputSignal.ofUnchecked(outputType, value);
        val result = execute(signal, priorFailure);
        if (result.failureState()) {
            throw new AccessDeniedException(ERROR_ACCESS_DENIED_POST_INVOCATION_OBLIGATION_FAILED);
        }
        return result.value() instanceof Present<?>(var v) ? v : null;
    }

    private boolean isVoidReturn() {
        val resolved = outputType.resolve();
        return resolved == void.class || resolved == Void.class;
    }

    /**
     * Fires {@link SubscriptionSignal} carrying the demand. Throws
     * {@link AccessDeniedException} on obligation-handler failure.
     * Suitable as a {@code LongConsumer} method reference for
     * {@code .doOnRequest(plan::enforceSubscription)}.
     */
    public void enforceSubscription(long demand) {
        enforceConstraintsOrThrow(SubscriptionSignal.of(demand));
    }

    /**
     * Fires {@link CancelSignal}. Suitable as a {@code Runnable} for
     * {@code .doOnCancel(plan::enforceCancel)}.
     */
    public void enforceCancel() {
        enforceConstraintsOrThrow(CancelSignal.INSTANCE);
    }

    /**
     * Fires {@link CompleteSignal}. Suitable as a {@code Runnable} for
     * {@code .doOnComplete(plan::enforceComplete)}.
     */
    public void enforceComplete() {
        enforceConstraintsOrThrow(CompleteSignal.INSTANCE);
    }

    /**
     * Fires {@link TerminationSignal}. Suitable as a {@code Runnable} for
     * {@code .doOnTerminate(plan::enforceTermination)}.
     */
    public void enforceTermination() {
        enforceConstraintsOrThrow(TerminationSignal.INSTANCE);
    }

    /**
     * Fires {@link AfterTerminationSignal}. Suitable as a {@code Runnable} for
     * {@code .doAfterTerminate(plan::enforceAfterTermination)}.
     */
    public void enforceAfterTermination() {
        enforceConstraintsOrThrow(AfterTerminationSignal.INSTANCE);
    }

    private void enforceConstraintsOrThrow(Signal signal) {
        if (execute(signal, false).failureState()) {
            throw new AccessDeniedException(ERROR_ACCESS_DENIED_AT_SIGNAL.formatted(signal.type()));
        }
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
