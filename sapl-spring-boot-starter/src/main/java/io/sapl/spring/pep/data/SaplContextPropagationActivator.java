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
package io.sapl.spring.pep.data;

import jakarta.annotation.PostConstruct;

import io.micrometer.context.ContextRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;

/**
 * Activates Reactor automatic context propagation for the SAPL enforcement
 * plan. Declared as a bean by any shim auto-configuration whose interceptors
 * need to read the plan from a synchronous Java method called inside a
 * Reactor flow (currently the R2DBC shim, since
 * {@code DatabaseClient.sql(...)} returns a non-publisher
 * {@code GenericExecuteSpec} synchronously).
 * <p>
 * On bean construction:
 * <ol>
 * <li>Registers an {@link EnforcementPlanThreadLocalAccessor} with
 * {@link ContextRegistry#getInstance()} so the plan is mirrored between
 * Reactor {@link reactor.util.context.Context} and a
 * {@link ThreadLocal} at every operator boundary.</li>
 * <li>Calls {@link Hooks#enableAutomaticContextPropagation()} once globally,
 * so Reactor performs the mirror on every operator.</li>
 * </ol>
 * The hook is JVM-wide. Activation is gated at the auto-configuration level
 * by both classpath presence and an explicit per-shim opt-out property so
 * that applications without SAPL shims active keep the default Reactor
 * behaviour. Operators see the activation in the startup log.
 */
@Slf4j
public class SaplContextPropagationActivator {

    private static final String LOG_ENABLING = "io.sapl.shim: enabling Reactor automatic context propagation"
            + " (registers EnforcementPlan as a ThreadLocal-propagated context value;" + " required by the R2DBC shim)";

    @PostConstruct
    public void activate() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new EnforcementPlanThreadLocalAccessor());
        Hooks.enableAutomaticContextPropagation();
        log.info(LOG_ENABLING);
    }
}
