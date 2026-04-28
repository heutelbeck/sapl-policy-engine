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
 * The {@link #mode()} attribute selects the lifecycle policy:
 * <ul>
 * <li>{@link StreamMode#TILL_DENIED} (default) — terminate with
 * {@code AccessDeniedException} on the first deny or unenforceable
 * PERMIT.</li>
 * <li>{@link StreamMode#DROP_WHILE_DENIED} — silently drop events while
 * denied; resume on the next enforceable PERMIT.</li>
 * <li>{@link StreamMode#ACCESS_AWARE} — drop data events while denied,
 * but emit transition events on every PENDING/PERMIT/DENY change so
 * the client can disambiguate denial from an idle source.</li>
 * </ul>
 * <p>
 * The four named aliases ({@link EnforceTillDenied},
 * {@link EnforceDropWhileDenied}, {@link EnforceAccessAware},
 * {@link EnforceRecoverableIfDenied}) are convenience meta-annotations
 * that pin {@link #mode()} to a specific value. Use this annotation
 * directly when the mode is selected by configuration or when a future
 * release allows the policy to override at runtime.
 *
 * @since 4.1.0
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface StreamEnforce {

    /**
     * @return the lifecycle policy. Defaults to {@link StreamMode#TILL_DENIED}.
     */
    StreamMode mode() default StreamMode.TILL_DENIED;

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
