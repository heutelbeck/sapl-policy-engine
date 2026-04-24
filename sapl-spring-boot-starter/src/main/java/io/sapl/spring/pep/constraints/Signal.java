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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.util.Maybe;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.ResolvableType;

/**
 * An event fired during enforcement at which constraint handlers may attach.
 * Sealed into self-contained
 * {@link VoidSignal}s and data-carrying {@link ValueSignal}s; every concrete
 * signal exposes its
 * {@link SignalType} via {@link #type()} for plan keying.
 */
public sealed interface Signal permits Signal.VoidSignal, Signal.ValueSignal {

    /** A signal that carries no value; only runners are admissible here. */
    sealed interface VoidSignal extends Signal
            permits CancelSignal, CompleteSignal, TerminationSignal, AfterTerminationSignal {
    }

    /**
     * A signal that carries a value of type {@code T}; mappers, consumers, and
     * runners are admissible here. {@link #valueType()} returns a
     * {@link ResolvableType} so generic information (e.g. the {@code String} in
     * {@code Mono<String>}) is preserved for provider dispatch.
     */
    sealed interface ValueSignal<T> extends Signal permits DecisionSignal, InputSignal, OutputSignal, ErrorSignal,
            SubscriptionSignal, R2dbcShimSignal, MongoDbShimSignal {
        T value();

        ResolvableType valueType();
    }

    /** Returns the reified type tag used as the plan key for this signal. */
    SignalType type();

    /** Fires when an authorization decision arrives from the PDP. */
    record DecisionSignal(AuthorizationDecision value) implements ValueSignal<AuthorizationDecision> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(AuthorizationDecision.class);
        public static final SignalType TYPE = new SignalType.ValueSignalType<>(DecisionSignal.class, VALUE_TYPE);

        public static DecisionSignal of(AuthorizationDecision decision) {
            return new DecisionSignal(decision);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /**
     * Fires before the RAP is invoked, carrying the intercepted method invocation.
     */
    record InputSignal(MethodInvocation value) implements ValueSignal<MethodInvocation> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(MethodInvocation.class);
        public static final SignalType TYPE = new SignalType.ValueSignalType<>(InputSignal.class, VALUE_TYPE);

        public static InputSignal of(MethodInvocation invocation) {
            return new InputSignal(invocation);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /**
     * Fires when the RAP emits an output of type {@code T} (return value or stream
     * item). The carried payload is a {@link Maybe} so that void-returning methods
     * can still fire the signal as {@link Maybe.Absent}, letting Runners attach
     * for side effects while Mappers and Consumers are skipped automatically. The
     * {@link ResolvableType} is the full generic view, so a provider may dispatch
     * on {@code Mono<String>} differently from {@code Mono<User>}.
     */
    record OutputSignal<T>(ResolvableType valueType, Maybe<T> maybeValue) implements ValueSignal<T> {

        /** Fires the signal with a value (which may itself be {@code null}). */
        public static <T> OutputSignal<T> of(Class<T> valueType, T value) {
            return new OutputSignal<>(ResolvableType.forClass(valueType), Maybe.of(value));
        }

        /** Fires the signal without a value, e.g. for {@code void} returns. */
        public static <T> OutputSignal<T> empty(Class<T> valueType) {
            return new OutputSignal<>(ResolvableType.forClass(valueType), Maybe.absent());
        }

        /** Fires the signal with a value using a full {@link ResolvableType}. */
        public static <T> OutputSignal<T> of(ResolvableType valueType, T value) {
            return new OutputSignal<>(valueType, Maybe.of(value));
        }

        /** Fires the signal without a value using a full {@link ResolvableType}. */
        public static <T> OutputSignal<T> empty(ResolvableType valueType) {
            return new OutputSignal<>(valueType, Maybe.absent());
        }

        /**
         * Erased-{@code T} factory for callers that hold {@code Class<?>} and
         * {@code Object} at runtime (e.g. AOP method interceptors).
         */
        public static OutputSignal<?> ofUnchecked(ResolvableType valueType, Object value) {
            return new OutputSignal<>(valueType, Maybe.of(value));
        }

        /**
         * Factory for AOP method interceptors. Inspects the invoked method's return
         * type, fires {@link #empty(ResolvableType)} for {@code void}/{@code Void}
         * returns and {@link #ofUnchecked(ResolvableType, Object)} otherwise, using
         * the method's generic return type so inner generics (e.g.
         * {@code Mono<String>}) are preserved.
         */
        public static OutputSignal<?> forResultOf(MethodInvocation invocation, Object returned) {
            val returnClass = invocation.getMethod().getReturnType();
            val valueType   = ResolvableType.forMethodReturnType(invocation.getMethod());
            if (returnClass == void.class || returnClass == Void.class) {
                return empty(valueType);
            }
            return ofUnchecked(valueType, returned);
        }

        @Override
        public T value() {
            return maybeValue instanceof Present<T>(var v) ? v : null;
        }

        @Override
        public SignalType type() {
            return typeFor(valueType);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public static <T> SignalType typeFor(ResolvableType valueType) {
            return new SignalType.ValueSignalType<>((Class<? extends ValueSignal<T>>) (Class) OutputSignal.class,
                    valueType);
        }

        /** Convenience overload keyed on a raw {@link Class}. */
        public static <T> SignalType typeFor(Class<T> valueType) {
            return typeFor(ResolvableType.forClass(valueType));
        }

        /**
         * Convenience for AOP method interceptors: keys on the invoked method's full
         * generic return type.
         */
        public static SignalType typeForReturnOf(MethodInvocation invocation) {
            return typeFor(ResolvableType.forMethodReturnType(invocation.getMethod()));
        }
    }

    /** Fires when the RAP raises or emits a {@link Throwable}. */
    record ErrorSignal(Throwable value) implements ValueSignal<Throwable> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(Throwable.class);
        public static final SignalType TYPE = new SignalType.ValueSignalType<>(ErrorSignal.class, VALUE_TYPE);

        public static ErrorSignal of(Throwable throwable) {
            return new ErrorSignal(throwable);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /** Fires on a downstream subscription request, carrying the demand count. */
    record SubscriptionSignal(Long value) implements ValueSignal<Long> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(Long.class);
        public static final SignalType TYPE = new SignalType.ValueSignalType<>(SubscriptionSignal.class, VALUE_TYPE);

        public static SubscriptionSignal of(Long demand) {
            return new SubscriptionSignal(demand);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /** Fires when a downstream subscriber cancels the subscription. */
    record CancelSignal() implements VoidSignal {
        public static final SignalType TYPE = new SignalType.VoidSignalType(CancelSignal.class);
        public static final CancelSignal INSTANCE = new CancelSignal();

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /** Fires when the upstream completes normally. */
    record CompleteSignal() implements VoidSignal {
        public static final SignalType TYPE = new SignalType.VoidSignalType(CompleteSignal.class);
        public static final CompleteSignal INSTANCE = new CompleteSignal();

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /** Fires on stream termination (any of: complete, error, cancel). */
    record TerminationSignal() implements VoidSignal {
        public static final SignalType TYPE = new SignalType.VoidSignalType(TerminationSignal.class);
        public static final TerminationSignal INSTANCE = new TerminationSignal();

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    /** Fires after termination cleanup has finished. */
    record AfterTerminationSignal() implements VoidSignal {
        public static final SignalType TYPE = new SignalType.VoidSignalType(AfterTerminationSignal.class);
        public static final AfterTerminationSignal INSTANCE = new AfterTerminationSignal();

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    // TODO: determine correct value type
    /**
     * Shim signal fired from inside an R2DBC RAP, carrying the generated query for
     * interception/rewriting.
     */
    record R2dbcShimSignal(Object value) implements ValueSignal<Object> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(Object.class);
        public static final SignalType TYPE = new SignalType.ValueSignalType<>(R2dbcShimSignal.class, VALUE_TYPE);

        public static R2dbcShimSignal of(Object value) {
            return new R2dbcShimSignal(value);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

    // TODO: determine correct value type
    /**
     * Shim signal fired from inside a MongoDB RAP, carrying the generated query for
     * interception/rewriting.
     */
    record MongoDbShimSignal(Object value) implements ValueSignal<Object> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(Object.class);
        public static final SignalType TYPE = new SignalType.ValueSignalType<>(MongoDbShimSignal.class, VALUE_TYPE);

        public static MongoDbShimSignal of(Object value) {
            return new MongoDbShimSignal(value);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return TYPE;
        }
    }

}
