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
import io.sapl.spring.pep.http.MutableHttpRequest;
import io.sapl.spring.pep.http.MutableHttpResponse;
import io.sapl.spring.util.Maybe;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.ResolvableType;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpRequest;

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
    sealed interface ValueSignal<T> extends Signal
            permits DecisionSignal, InputSignal, OutputSignal, ErrorSignal, SubscriptionSignal, SqlShimSignal,
            MongoDbQueryShimSignal, HttpRequestSignal, HttpRequestMutationSignal, HttpResponseSignal, HttpDenialSignal {
        T value();

        ResolvableType valueType();
    }

    /** Returns the reified type tag used as the plan key for this signal. */
    SignalType type();

    /** Fires when an authorization decision arrives from the PDP. */
    record DecisionSignal(AuthorizationDecision value) implements ValueSignal<AuthorizationDecision> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(AuthorizationDecision.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(DecisionSignal.class, VALUE_TYPE);

        public static DecisionSignal of(AuthorizationDecision decision) {
            return new DecisionSignal(decision);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Fires before the RAP is invoked, carrying the intercepted method invocation.
     */
    record InputSignal(MethodInvocation value) implements ValueSignal<MethodInvocation> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(MethodInvocation.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(InputSignal.class, VALUE_TYPE);

        public static InputSignal of(MethodInvocation invocation) {
            return new InputSignal(invocation);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
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
        public static OutputSignal<Object> ofUnchecked(ResolvableType valueType, Object value) {
            return new OutputSignal<>(valueType, Maybe.of(value));
        }

        /**
         * Factory for AOP method interceptors. Inspects the invoked method's return
         * type, fires {@link #empty(ResolvableType)} for {@code void}/{@code Void}
         * returns and {@link #ofUnchecked(ResolvableType, Object)} otherwise, using
         * the method's generic return type so inner generics (e.g.
         * {@code Mono<String>}) are preserved.
         */
        public static OutputSignal<Object> forResultOf(MethodInvocation invocation, Object returned) {
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
        public static SignalType typeFor(ResolvableType valueType) {
            return new SignalType.ValueSignalType<>(
                    (Class<? extends ValueSignal<Object>>) (Class<?>) OutputSignal.class, valueType);
        }

        /** Convenience overload keyed on a raw {@link Class}. */
        public static SignalType typeFor(Class<?> valueType) {
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
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(ErrorSignal.class, VALUE_TYPE);

        public static ErrorSignal of(Throwable throwable) {
            return new ErrorSignal(throwable);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /** Fires on a downstream subscription request, carrying the demand count. */
    record SubscriptionSignal(Long value) implements ValueSignal<Long> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(Long.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(SubscriptionSignal.class,
                VALUE_TYPE);

        public static SubscriptionSignal of(Long demand) {
            return new SubscriptionSignal(demand);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /** Fires when a downstream subscriber cancels the subscription. */
    record CancelSignal() implements VoidSignal {
        public static final SignalType SIGNAL_TYPE = new SignalType.VoidSignalType(CancelSignal.class);
        public static final CancelSignal INSTANCE = new CancelSignal();

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /** Fires when the upstream completes normally. */
    record CompleteSignal() implements VoidSignal {
        public static final SignalType SIGNAL_TYPE = new SignalType.VoidSignalType(CompleteSignal.class);
        public static final CompleteSignal INSTANCE = new CompleteSignal();

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /** Fires on stream termination (any of: complete, error, cancel). */
    record TerminationSignal() implements VoidSignal {
        public static final SignalType SIGNAL_TYPE = new SignalType.VoidSignalType(TerminationSignal.class);
        public static final TerminationSignal INSTANCE = new TerminationSignal();

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /** Fires after termination cleanup has finished. */
    record AfterTerminationSignal() implements VoidSignal {
        public static final SignalType SIGNAL_TYPE = new SignalType.VoidSignalType(AfterTerminationSignal.class);
        public static final AfterTerminationSignal INSTANCE = new AfterTerminationSignal();

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Shim signal carrying a raw SQL string just before driver dispatch. Fired by
     * wrappers around {@code DatabaseClient.sql(String)} (R2DBC), JDBC templates,
     * and JPA native query execution. Mappers attached here may textually rewrite
     * the SQL (e.g. inject WHERE clauses) before the underlying driver receives
     * it. Dialect-agnostic at the signal level; mapper authors handle dialect
     * concerns.
     */
    record SqlShimSignal(String value) implements ValueSignal<String> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(String.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(SqlShimSignal.class, VALUE_TYPE);

        public static SqlShimSignal of(String sql) {
            return new SqlShimSignal(sql);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Shim signal carrying a Spring Data MongoDB {@link Query} just before
     * driver dispatch. Fired by wrappers around {@code MongoTemplate} and
     * {@code ReactiveMongoTemplate}. Raw {@code @Query}-annotated methods parse
     * into {@code BasicQuery extends Query} and travel the same path, so a
     * single shim signal covers all Mongo query origins. The carried type is
     * Spring Data Mongo's
     * {@code org.springframework.data.mongodb.core.query.Query},
     * not the relational query type.
     */
    record MongoDbQueryShimSignal(Query value) implements ValueSignal<Query> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(Query.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(MongoDbQueryShimSignal.class,
                VALUE_TYPE);

        public static MongoDbQueryShimSignal of(Query query) {
            return new MongoDbQueryShimSignal(query);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Fires inside the HTTP authorization manager once the PDP decision is
     * known. Carries a read-only view of the inbound request as a Spring
     * {@link HttpRequest}. Use {@code Consumer} or {@code Runner}
     * handlers for audit, metrics, or rate-limit checks. The request is
     * treated as read-only at this point. Mutations belong to
     * {@link HttpRequestMutationSignal}.
     */
    record HttpRequestSignal(HttpRequest value) implements ValueSignal<HttpRequest> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(HttpRequest.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(HttpRequestSignal.class,
                VALUE_TYPE);

        public static HttpRequestSignal of(HttpRequest request) {
            return new HttpRequestSignal(request);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Fires from the SAPL HTTP filter on the permit path, before the
     * request reaches downstream filters and the controller. Carries a
     * {@link MutableHttpRequest} that handlers may use to set or remove
     * headers and request attributes.
     */
    record HttpRequestMutationSignal(MutableHttpRequest value) implements ValueSignal<MutableHttpRequest> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(MutableHttpRequest.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(HttpRequestMutationSignal.class,
                VALUE_TYPE);

        public static HttpRequestMutationSignal of(MutableHttpRequest request) {
            return new HttpRequestMutationSignal(request);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Fires from the SAPL HTTP filter on the permit path after the
     * controller has returned, before the response is committed to the
     * client. Carries a {@link MutableHttpResponse}. Consumer handlers
     * may observe the response (status, headers) or modify it (set
     * status, set or add headers, write body). The signal serves both
     * observation and modification through the standard handler shapes.
     */
    record HttpResponseSignal(MutableHttpResponse value) implements ValueSignal<MutableHttpResponse> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(MutableHttpResponse.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(HttpResponseSignal.class,
                VALUE_TYPE);

        public static HttpResponseSignal of(MutableHttpResponse response) {
            return new HttpResponseSignal(response);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

    /**
     * Fires from the SAPL access-denied handler when an authenticated
     * request is denied by the policy. Carries a
     * {@link MutableHttpResponse} that handlers may use to set the
     * status, write headers, or issue a redirect. Anonymous denials route
     * through Spring's authentication entry point and do not fire this
     * signal.
     */
    record HttpDenialSignal(MutableHttpResponse value) implements ValueSignal<MutableHttpResponse> {
        public static final ResolvableType VALUE_TYPE = ResolvableType.forClass(MutableHttpResponse.class);
        public static final SignalType SIGNAL_TYPE = new SignalType.ValueSignalType<>(HttpDenialSignal.class,
                VALUE_TYPE);

        public static HttpDenialSignal of(MutableHttpResponse response) {
            return new HttpDenialSignal(response);
        }

        @Override
        public ResolvableType valueType() {
            return VALUE_TYPE;
        }

        @Override
        public SignalType type() {
            return SIGNAL_TYPE;
        }
    }

}
