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
package io.sapl.pdp;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.plugins.PluginsSource;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.time.InstantSource;
import java.util.List;

/**
 * Bundle of components produced by {@link PolicyDecisionPointBuilder}.
 * Holds the blocking-engine PDP plus the broker, attribute broker,
 * voter source, and any configured interceptors/listeners.
 * <p>
 * Reactor-free by design: this record is the foundation for
 * Reactor-flavoured wrappers (see
 * {@code ReactivePDPComponents}/{@code ReactivePolicyDecisionPointBuilder}
 * in {@code sapl-reactive-pdp}) without forcing a Reactor dependency on
 * the core engine.
 * <p>
 * Implements {@link AutoCloseable} so the whole bundle can be released
 * in one call (or via a try-with-resources block) when the embedding
 * application shuts down. {@link #close()} is idempotent: each held
 * component is required to tolerate being closed more than once.
 */
@Slf4j
public record PDPComponents(
        BlockingPolicyDecisionPoint pdp,
        PdpVoterSource pdpVoterSource,
        FunctionBroker functionBroker,
        AttributeBroker attributeBroker,
        @Nullable PDPConfigurationSource source,
        InstantSource timestampSource,
        boolean ownsTimestampSource,
        List<DecisionInterceptor> decisionInterceptors,
        List<SubscriptionLifecycleListener> lifecycleListeners,
        @Nullable PluginsSource pluginsSource,
        boolean ownsPluginsSource,
        @Nullable AttributeRepository ownedRepository) implements AutoCloseable {

    private static final String WARN_ERROR_CLOSING_RESOURCE = "Error closing {}: {}";

    /**
     * Closes every held component. Resources that are not
     * {@link AutoCloseable} are skipped silently. Exceptions thrown by
     * individual components are logged and otherwise ignored so a
     * single failure cannot prevent the rest of the cleanup.
     */
    @Override
    public void close() {
        // Close the timestamp source only when owned. A caller-supplied source is the
        // caller's to close.
        if (ownsTimestampSource) {
            closeQuietly(timestampSource);
        }
        closeAll(source, pdpVoterSource, attributeBroker, functionBroker, decisionInterceptors, lifecycleListeners);
        // Builder-owned only. A withPluginsSource source stays caller-owned.
        if (ownsPluginsSource) {
            closeQuietly(pluginsSource);
        }
        // Builder-created default repository only. A withRepository or
        // withAttributeBroker
        // repository stays caller-owned (ownedRepository is null then). Closing it
        // shuts
        // down its scheduler, otherwise that thread leaks for the PDP's lifetime.
        closeQuietly(ownedRepository);
    }

    private static void closeAll(Object... resources) {
        for (val resource : resources) {
            if (resource instanceof Iterable<?> iterable) {
                for (val item : iterable) {
                    closeQuietly(item);
                }
            } else {
                closeQuietly(resource);
            }
        }
    }

    private static void closeQuietly(@Nullable Object resource) {
        if (!(resource instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn(WARN_ERROR_CLOSING_RESOURCE, resource.getClass().getSimpleName(), e.getMessage());
        }
    }
}
