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
package io.sapl.spring.method.metadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Establishes a streaming reactive PEP on a method that returns a
 * {@link reactor.core.publisher.Flux Flux} of items. The PEP keeps the
 * subscription to the PDP open across the lifetime of the returned Flux,
 * re-planning per decision and gating per item.
 * <p>
 * The PDP decision verb drives the streaming-state response under the
 * strict fail-closed discipline:
 * <ul>
 * <li><b>PERMIT</b> with enforceable obligations: items flow.</li>
 * <li><b>SUSPEND</b>: subscription preserved, items dropped silently
 * until a later PERMIT resumes the flow.</li>
 * <li><b>DENY</b>, <b>INDETERMINATE</b>, <b>NOT_APPLICABLE</b>, and a
 * PERMIT whose decision-scoped enforcement fails: subscription
 * terminates with
 * {@link org.springframework.security.access.AccessDeniedException
 * AccessDeniedException}. Per-item obligation failure terminates
 * identically.</li>
 * </ul>
 * <p>
 * Two optional flags refine the PEP-side behaviour:
 * <ul>
 * <li>{@link #signalTransitions()} (default {@code false}). Surfaces
 * every suspend/resume boundary to the subscriber as a non-terminal
 * exception on the error channel
 * ({@link org.springframework.security.access.AccessDeniedException
 * AccessDeniedException} on entry to suspended,
 * {@link io.sapl.spring.pep.streaming.AccessGrantedException} on resume).
 * Subscribers consume the signals with {@code onErrorContinue} or via
 * {@link io.sapl.spring.pep.streaming.TransitionSignals}.</li>
 * <li>{@link #pauseRapDuringSuspend()} (default {@code false}). Manages
 * the RAP connection while suspended. When {@code false}, the RAP
 * subscription stays connected and items keep arriving; the PEP drops
 * them silently. When {@code true}, the PEP disposes the RAP
 * subscription on entering suspended and re-subscribes on resume.</li>
 * </ul>
 *
 * @since 4.1.0
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface StreamEnforce {

    /**
     * Whether boundary transitions (entering suspended, resuming to
     * permitting) are observable to the subscriber as non-terminal
     * exceptions on the error channel. Subscribers consume the signals
     * with {@code onErrorContinue} or via
     * {@link io.sapl.spring.pep.streaming.TransitionSignals}; without
     * such handling the first signal terminates the subscription.
     *
     * @return {@code true} to surface boundary transitions to the
     * subscriber; {@code false} (default) for silent transitions.
     */
    boolean signalTransitions() default false;

    /**
     * Whether the protected method (RAP) subscription is disposed while
     * the PEP is in suspended state. Default {@code false}: RAP stays
     * connected and items keep arriving; the PEP drops them silently
     * (lower latency on resume, RAP-side state preserved). {@code true}:
     * the PEP disposes the RAP subscription on entering suspended and
     * re-subscribes on resume (stops RAP-side side effects during
     * suspension at the cost of re-subscription latency on resume).
     *
     * @return {@code true} to pause the RAP subscription while
     * suspended; {@code false} (default) to keep the RAP connected and
     * drop items in the PEP.
     */
    boolean pauseRapDuringSuspend() default false;

    /**
     * @return SpEL expression for the subject in the authorization
     * subscription. If empty, the PEP derives the subject from the current
     * security context.
     */
    String subject() default "";

    /**
     * @return SpEL expression for the action. If empty, the PEP derives it
     * from the invoked method.
     */
    String action() default "";

    /**
     * @return SpEL expression for the resource. If empty, the PEP derives
     * it from reflection on the invocation.
     */
    String resource() default "";

    /**
     * @return SpEL expression for the environment. If empty, no environment
     * is attached to the subscription.
     */
    String environment() default "";

    /**
     * @return SpEL expression for the secrets payload. Must evaluate to an
     * object. If empty, no secrets are attached.
     */
    String secrets() default "";
}
