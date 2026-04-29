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
 * Legacy alias equivalent to
 * {@code @StreamEnforce(survivesDeny = true, signalTransitions = true)}.
 * Identical behaviour to {@link EnforceAccessAware}; preserved for source
 * compatibility with earlier 4.x releases.
 * <p>
 * The subscription survives deny decisions and surfaces every boundary
 * crossing to the subscriber as a non-terminal exception on the error
 * channel. Consume via
 * {@link io.sapl.spring.pep.streaming.RecoverableFluxes} or
 * {@code onErrorContinue}.
 * <p>
 * {@link EnforceAccessAware} is preferred for new code; the
 * "Recoverable" name leaked from {@code Flux.recoverFromError} semantics
 * and is no longer the cleanest description (the subscription is informed
 * of the access state, not literally recovering from anything).
 *
 * @see StreamEnforce
 * @see EnforceAccessAware
 */
@Inherited
@Documented
@StreamEnforce(survivesDeny = true, signalTransitions = true)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface EnforceRecoverableIfDenied {

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
