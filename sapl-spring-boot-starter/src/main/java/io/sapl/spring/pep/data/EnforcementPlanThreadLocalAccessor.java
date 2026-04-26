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

import org.jspecify.annotations.Nullable;

import io.micrometer.context.ThreadLocalAccessor;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;

/**
 * Micrometer {@link ThreadLocalAccessor} that bridges the active
 * {@link EnforcementPlan} between the Reactor context (where the reactive PEP
 * stores it) and the {@link EnforcementPlanContext}'s blocking
 * {@link ThreadLocal} (read by synchronous shim interceptors).
 * <p>
 * Registered with {@link io.micrometer.context.ContextRegistry} by
 * {@link SaplContextPropagationActivator} so that
 * {@code Hooks.enableAutomaticContextPropagation()} copies the plan to the
 * thread on every Reactor operator boundary, lets a synchronous interceptor
 * inside a Reactor flow read it, and clears it on the way out.
 */
public class EnforcementPlanThreadLocalAccessor implements ThreadLocalAccessor<EnforcementPlan> {

    /**
     * The key used in Reactor {@link reactor.util.context.Context} for the
     * plan; matches {@link EnforcementPlanContext#REACTOR_KEY} so a single
     * Context entry is observable on both sides of the bridge.
     */
    public static final String KEY = EnforcementPlanContext.REACTOR_KEY;

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public @Nullable EnforcementPlan getValue() {
        return EnforcementPlanContext.currentBlocking().orElse(null);
    }

    @Override
    public void setValue(EnforcementPlan value) {
        EnforcementPlanContext.bindBlocking(value);
    }

    @Override
    public void setValue() {
        EnforcementPlanContext.bindBlocking(null);
    }
}
