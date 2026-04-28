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
 * Streaming PEP alias that pins {@link StreamEnforce#mode()} to
 * {@link StreamMode#ACCESS_AWARE}.
 * <p>
 * Equivalent to {@code @StreamEnforce(mode = ACCESS_AWARE)}. The
 * subscription stays alive across denials. Data events are dropped while
 * denied, and transition events are emitted on every PENDING/PERMIT/DENY
 * change so the client can disambiguate denial from an idle source.
 * <p>
 * Use this annotation when the client needs to know the access state in
 * real time (UI badges, telemetry dashboards, mobile apps moving across
 * authorization zones). For the same semantic under the legacy name, see
 * {@link EnforceRecoverableIfDenied}.
 *
 * @see StreamEnforce
 * @see StreamMode#ACCESS_AWARE
 * @see EnforceRecoverableIfDenied
 */
@Inherited
@Documented
@StreamEnforce(mode = StreamMode.ACCESS_AWARE)
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
