/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
 * The @EnforceDropWhileDenied annotation establishes a reactive policy
 * enforcement point (PEP). The PEP is only applicable to methods returning a
 * {@link org.reactivestreams.Publisher Publisher}, i.e., a
 * {@link reactor.core.publisher.Flux Flux} or a
 * {@link reactor.core.publisher.Mono Mono}.
 * <p>
 * The publisher returned by the method is wrapped by the PEP. The PEP starts
 * processing, i.e, sending a subscription to the PDP, upon subscription time.
 * <p>
 * The established PEP also wires in matching handlers for obligations and
 * advice into the matching signal paths of the publisher.
 * <p>
 * Subscribe to the resource after the first decision, make it a hot source.
 * Filter out all events from the data stream wile the most recent decision is
 * not PERMIT.
 * <p>
 * Keep the subscription alive as long as the client does.
 * <p>
 * The client is not aware of access denied events.
 * <p>
 * The parameters subject, action, resource, and environment can be used to
 * explicitly set the corresponding keys in the SAPL authorization subscription,
 * assuming that the Spring context and ObjectMapper are configured to be able
 * to serialize the resulting value into JSON.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface EnforceDropWhileDenied {

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     * the subject in the authorization subscription to the PDP. If empty, the PEP
     * attempts to derive a guess to describe the subject based on the current
     * Principal.
     */
    String subject() default "";

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     * the action in the authorization subscription to the PDP. If empty, the PEP
     * attempts to derive a guess to describe the action based on reflection.
     */
    String action() default "";

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     * the action in the authorization subscription to the PDP. If empty, the PEP
     * attempts to derive a guess to describe the resource based on reflection.
     */
    String resource() default "";

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     * the action in the authorization subscription to the PDP. If empty, no
     * environment is set in the subscription.
     */
    String environment() default "";

    /**
     * @return the type of the generic parameter of the return type being secured.
     * Helps due to Java type erasure at runtime. Defaults to {@code Object.class}.
     */
    Class<?> genericsType() default Object.class;

}
