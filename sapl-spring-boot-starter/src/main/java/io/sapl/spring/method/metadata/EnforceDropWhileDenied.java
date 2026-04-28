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
 * {@link StreamMode#DROP_WHILE_DENIED}.
 * <p>
 * Equivalent to {@code @StreamEnforce(mode = DROP_WHILE_DENIED)}. The
 * subscription stays alive across denials and silently drops events while
 * denied; emission resumes when a fresh enforceable PERMIT arrives. The
 * client cannot distinguish denial from an idle source. Use when denial
 * itself must be hidden from the client (insider-threat monitoring,
 * legacy clients that cannot renegotiate).
 *
 * @see StreamEnforce
 * @see StreamMode#DROP_WHILE_DENIED
 */
@Inherited
@Documented
@StreamEnforce(mode = StreamMode.DROP_WHILE_DENIED)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface EnforceDropWhileDenied {

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
