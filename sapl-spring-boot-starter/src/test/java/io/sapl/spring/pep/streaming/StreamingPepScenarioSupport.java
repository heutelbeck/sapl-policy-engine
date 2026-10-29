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
package io.sapl.spring.pep.streaming;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.util.SimpleMethodInvocation;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.publisher.TestPublisher;
import tools.jackson.databind.json.JsonMapper;

final class StreamingPepScenarioSupport implements AutoCloseable {

    static final Duration   TIMEOUT = Duration.ofSeconds(3);
    static final SignalType OUTPUT  = OutputSignal.typeFor(Object.class);

    final List<RapCall> calls                  = new CopyOnWriteArrayList<>();
    final List<String>  trace                  = new CopyOnWriteArrayList<>();
    final List<Object>  observed               = new CopyOnWriteArrayList<>();
    final AtomicInteger pdpSubscriptions       = new AtomicInteger();
    final AtomicInteger pdpCancellations       = new AtomicInteger();
    final AtomicInteger downstreamTerminations = new AtomicInteger();

    private final Map<Value, Supplier<List<ScopedConstraintHandler>>> bindings      = new ConcurrentHashMap<>();
    private final List<Subscription>                                  subscriptions = new CopyOnWriteArrayList<>();
    private TestPublisher<AuthorizationDecision>                      pdp           = TestPublisher.create();
    Function<RapCall, Flux<Object>>                                   rapBody       = call -> call.source.flux();

    void reset() {
        close();
        calls.clear();
        trace.clear();
        observed.clear();
        bindings.clear();
        subscriptions.clear();
        pdpSubscriptions.set(0);
        pdpCancellations.set(0);
        downstreamTerminations.set(0);
        pdp     = TestPublisher.create();
        rapBody = call -> call.source.flux();
    }

    Flux<AuthorizationDecision> decisions() {
        return pdp.flux().doOnSubscribe(subscription -> {
            subscriptions.add(subscription);
            pdpSubscriptions.incrementAndGet();
        }).doOnCancel(pdpCancellations::incrementAndGet);
    }

    Flux<Object> pipeline(boolean signalTransitions) {
        return StreamingPipeline.createPrepared(decisions(), this::plan, this::prepareInvocation, signalTransitions)
                .doOnTerminate(downstreamTerminations::incrementAndGet);
    }

    private Supplier<? extends Flux<?>> prepareInvocation(EnforcementPlan plan) {
        try {
            val method     = StreamingPepScenarioSupport.class.getDeclaredMethod("open", String.class, String.class);
            val invocation = new SimpleMethodInvocation(this, method, "all-devices", "09:00");
            if (plan.execute(InputSignal.of(invocation), false).failureState()) {
                throw new AccessDeniedException("Input obligation failed");
            }
            return () -> open((String) invocation.getArguments()[0], (String) invocation.getArguments()[1]);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    Flux<Object> open(String scope, String start) {
        val call = new RapCall(scope, start);
        calls.add(call);
        trace.add("invoke:" + scope);
        return rapBody.apply(call).doOnSubscribe(subscription -> {
            subscriptions.add(subscription);
            call.subscriptions.incrementAndGet();
        }).doOnRequest(call.requests::add).doOnCancel(() -> {
            call.cancellations.incrementAndGet();
            trace.add("dispose:" + scope);
            val action = call.cancelAction;
            call.cancelAction = () -> {};
            action.run();
        });
    }

    void permit(Value... obligations) {
        decide(Decision.PERMIT, List.of(obligations), List.of());
    }

    void suspend(Value... obligations) {
        decide(Decision.SUSPEND, List.of(obligations), List.of());
    }

    void decide(Decision decision, List<Value> obligations, List<Value> advice) {
        pdp.next(new AuthorizationDecision(decision, new ArrayValue(obligations), new ArrayValue(advice),
                Value.UNDEFINED));
    }

    EnforcementPlan plan(AuthorizationDecision decision) {
        val mapper  = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val planner = new EnforcementPlanner(List.of(this::handlers), mapper);
        return planner.plan(decision,
                Set.of(InputSignal.SIGNAL_TYPE, OUTPUT, Signal.DecisionSignal.SIGNAL_TYPE,
                        Signal.SubscriptionSignal.SIGNAL_TYPE, Signal.ErrorSignal.SIGNAL_TYPE,
                        Signal.CancelSignal.SIGNAL_TYPE, Signal.CompleteSignal.SIGNAL_TYPE,
                        Signal.TerminationSignal.SIGNAL_TYPE, Signal.AfterTerminationSignal.SIGNAL_TYPE));
    }

    List<ScopedConstraintHandler> handlers(Value constraint, Set<SignalType> supportedSignals) {
        val factory = bindings.get(constraint);
        if (factory == null) {
            return List.of();
        }
        val outputType = SignalType.findIn(supportedSignals, OutputSignal.class).orElseThrow();
        return factory.get().stream().map(assignment -> {
            if (OUTPUT.equals(assignment.signalType())) {
                return new ScopedConstraintHandler(assignment.handler(), outputType, assignment.priority());
            }
            return assignment;
        }).toList();
    }

    Value inputScope(String scope) {
        val constraint = Value.ofObject(Map.of("type", Value.of("deviceScope"), "scope", Value.of(scope)));
        bind(constraint, InputSignal.SIGNAL_TYPE, 10, () -> (ConstraintHandler.Mapper<MethodInvocation>) invocation -> {
            trace.add("input:" + scope);
            invocation.getArguments()[0] = scope;
            return invocation;
        });
        return constraint;
    }

    Value appendScope(String suffix) {
        val constraint = Value.ofObject(Map.of("type", Value.of("appendScope"), "suffix", Value.of(suffix)));
        bind(constraint, InputSignal.SIGNAL_TYPE, 10, () -> (ConstraintHandler.Mapper<MethodInvocation>) invocation -> {
            trace.add("input:" + suffix);
            invocation.getArguments()[0] = invocation.getArguments()[0] + suffix;
            return invocation;
        });
        return constraint;
    }

    Value runner(String name, SignalType signal, int priority, Runnable action) {
        val constraint = Value.of(name);
        bind(constraint, signal, priority, () -> (ConstraintHandler.Runner) () -> {
            trace.add(name);
            action.run();
        });
        return constraint;
    }

    Value inputAudit(String name) {
        return runner(name, InputSignal.SIGNAL_TYPE, 20, () -> {});
    }

    Value inputFailure() {
        return runner("input-failure", InputSignal.SIGNAL_TYPE, 10, () -> {
            throw new IllegalStateException("Input obligation could not be fulfilled");
        });
    }

    Value decisionFailure() {
        return runner("decision-failure", Signal.DecisionSignal.SIGNAL_TYPE, 10, () -> {
            throw new IllegalStateException("Decision obligation could not be fulfilled");
        });
    }

    Value outputMapper(String name, int priority, UnaryOperator<Object> mapping) {
        val constraint = Value.of(name);
        bind(constraint, OUTPUT, priority, () -> (ConstraintHandler.Mapper<Object>) input -> {
            trace.add(name);
            return mapping.apply(input);
        });
        return constraint;
    }

    Value outputFailure() {
        return outputMapper("output-failure", 10, input -> {
            throw new IllegalStateException("Malformed sensor record");
        });
    }

    void bind(Value constraint, SignalType signal, int priority, Supplier<ConstraintHandler<?>> handler) {
        bindings.put(constraint, () -> List.of(new ScopedConstraintHandler(handler.get(), signal, priority)));
    }

    void emit(int invocation, Object item) {
        calls.get(invocation).source.next(item);
    }

    void assertPdpOpen() {
        pdp.assertSubscribers(1).assertNotCancelled();
    }

    @Override
    public void close() {
        subscriptions.forEach(Subscription::cancel);
    }

    static final class RapCall {
        final String                scope;
        final String                start;
        final TestPublisher<Object> source        = TestPublisher
                .createNoncompliant(TestPublisher.Violation.DEFER_CANCELLATION);
        final AtomicInteger         subscriptions = new AtomicInteger();
        final AtomicInteger         cancellations = new AtomicInteger();
        final List<Long>            requests      = new CopyOnWriteArrayList<>();
        Runnable                    cancelAction  = () -> {};

        RapCall(String scope, String start) {
            this.scope = scope;
            this.start = start;
        }
    }
}
