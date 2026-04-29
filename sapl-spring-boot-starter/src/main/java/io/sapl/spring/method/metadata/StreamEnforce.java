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
 * The PEP behaviour is parameterised by two orthogonal flags:
 * <ul>
 * <li>{@link #survivesDeny()} (default {@code false}) selects the deny
 * strategy. When {@code false} (the secure default), the first deny
 * terminates the subscription with
 * {@link org.springframework.security.access.AccessDeniedException
 * AccessDeniedException}. When {@code true}, the subscription is preserved
 * across denies; data items are dropped while denied; a later PERMIT
 * resumes the flow.</li>
 * <li>{@link #signalTransitions()} (default {@code false}) selects whether
 * boundary crossings are observable to the subscriber. When {@code true},
 * the deny boundary surfaces as a non-terminal {@code AccessDeniedException}
 * and the grant boundary surfaces as a non-terminal
 * {@link io.sapl.spring.pep.streaming.AccessGrantedException}, both via the
 * error channel for consumption with {@code onErrorContinue} or the SAPL
 * helper {@link io.sapl.spring.pep.streaming.RecoverableFluxes}. When
 * {@code false}, the subscription experiences denies silently. Ignored
 * when {@link #survivesDeny()} is {@code false} (no surviving subscription
 * to signal on).</li>
 * </ul>
 * <p>
 * The four named aliases ({@link EnforceTillDenied},
 * {@link EnforceDropWhileDenied}, {@link EnforceAccessAware},
 * {@link EnforceRecoverableIfDenied}) are convenience meta-annotations
 * that pin the two flags to specific values. Use this annotation directly
 * when configuration drives the choice or when a future release allows the
 * policy to override at runtime.
 *
 * @since 4.1.0
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface StreamEnforce {

    /**
     * Whether the subscription survives a deny decision and may resume on a
     * later PERMIT. Default {@code false}: the first deny terminates the
     * subscription with
     * {@link org.springframework.security.access.AccessDeniedException
     * AccessDeniedException}, matching one-shot {@code @PreEnforce}
     * semantics. Setting to {@code true} opts into a streaming-PEP-specific
     * "deny is temporary" behaviour where data items are dropped while
     * denied and the subscription resumes on the next enforceable PERMIT.
     *
     * @return {@code false} (secure default) for terminal-on-deny behaviour;
     * {@code true} to opt into surviving the deny.
     */
    boolean survivesDeny() default false;

    /**
     * Whether boundary transitions (entering Denying, entering Permitting)
     * are observable to the subscriber as non-terminal exceptions on the
     * error channel. Effective only when {@link #survivesDeny()} is
     * {@code true}; ignored otherwise (no surviving subscription to signal
     * on). Subscribers consume the signals with {@code onErrorContinue} or
     * via {@link io.sapl.spring.pep.streaming.RecoverableFluxes}; without
     * such handling the first signal terminates the subscription, which
     * defeats the purpose of {@code survivesDeny = true}.
     *
     * @return {@code true} to surface boundary transitions to the
     * subscriber; {@code false} (default) for silent transitions.
     */
    boolean signalTransitions() default false;

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
