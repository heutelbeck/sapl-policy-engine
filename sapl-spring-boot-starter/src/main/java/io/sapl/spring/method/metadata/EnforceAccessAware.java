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

import org.springframework.core.annotation.AliasFor;

/**
 * Streaming PEP alias equivalent to
 * {@code @StreamEnforce(survivesDeny = true, signalTransitions = true)}.
 * <p>
 * The subscription survives deny decisions and surfaces every boundary
 * crossing to the subscriber as a non-terminal exception on the error
 * channel: an
 * {@link org.springframework.security.access.AccessDeniedException} on
 * entry into Denying, an
 * {@link io.sapl.spring.pep.streaming.AccessGrantedException} on entry
 * into Permitting (initial grant or recovery from Denying). Subscribers
 * consume both via {@code onErrorContinue} or via
 * {@link io.sapl.spring.pep.streaming.RecoverableFluxes}.
 * <p>
 * Use when the client needs to know the access state in real time (UI
 * badges, telemetry dashboards, mobile apps moving across authorization
 * zones). For the same semantic under the legacy name, see
 * {@link EnforceRecoverableIfDenied}.
 *
 * @see StreamEnforce
 * @see EnforceRecoverableIfDenied
 */
@Inherited
@Documented
@StreamEnforce(survivesDeny = true, signalTransitions = true)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface EnforceAccessAware {

    /** {@inheritDoc} */
    @AliasFor(annotation = StreamEnforce.class)
    String subject() default "";

    /** {@inheritDoc} */
    @AliasFor(annotation = StreamEnforce.class)
    String action() default "";

    /** {@inheritDoc} */
    @AliasFor(annotation = StreamEnforce.class)
    String resource() default "";

    /** {@inheritDoc} */
    @AliasFor(annotation = StreamEnforce.class)
    String environment() default "";

    /** {@inheritDoc} */
    @AliasFor(annotation = StreamEnforce.class)
    String secrets() default "";
}
