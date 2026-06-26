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
package io.sapl.pdp.plugins;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of every plugin-contributed value the PDP needs at
 * runtime. The plugin engine produces one bundle per atomic update of
 * the loaded plugin set.
 *
 * @param functionBroker the broker used for function evaluation
 * @param decisionInterceptors observers invoked per emitted decision
 * @param lifecycleListeners observers invoked on subscribe and unsubscribe
 *
 * @since 4.1.0
 */
public record PluginsBundle(
        FunctionBroker functionBroker,
        List<DecisionInterceptor> decisionInterceptors,
        List<SubscriptionLifecycleListener> lifecycleListeners) {

    /**
     * Defensive-copy. Both lists become unmodifiable.
     */
    public PluginsBundle {
        Objects.requireNonNull(functionBroker, "functionBroker");
        decisionInterceptors = List.copyOf(decisionInterceptors);
        lifecycleListeners   = List.copyOf(lifecycleListeners);
    }

    /**
     * Convenience constructor for the no-interceptors case.
     *
     * @param functionBroker the broker
     */
    public PluginsBundle(FunctionBroker functionBroker) {
        this(functionBroker, List.of(), List.of());
    }
}
