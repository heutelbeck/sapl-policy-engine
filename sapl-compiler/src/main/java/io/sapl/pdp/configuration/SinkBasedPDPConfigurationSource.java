/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.configuration;

import io.sapl.api.pdp.PDPConfiguration;
import lombok.NonNull;
import lombok.val;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A push-based PDP configuration source that accepts configurations via
 * programmatic API. Useful for REST endpoint
 * integration, webhook handlers, or testing scenarios where configurations are
 * injected externally.
 * <p>
 * When configurations are pushed via
 * {@link #pushConfiguration(PDPConfiguration)}, the callback provided at
 * construction is invoked with the new configuration.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * // Create source with callback to configuration register
 * var source = new SinkBasedPDPConfigurationSource(
 *     config -> register.loadConfiguration(config, true)
 * );
 *
 * // Push a configuration update (will invoke callback)
 * source.pushConfiguration(new PDPConfiguration("production", ...));
 *
 * // Query available PDP IDs
 * Set<String> availableIds = source.getAvailablePdpIds();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The set of known PDP IDs is managed with
 * synchronized access, and the callback is invoked
 * synchronously within the calling thread.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>PDP identifier validation:</b> All PDP identifiers are validated
 * against a strict pattern (alphanumeric,
 * hyphens, underscores, dots) with a maximum length of 255 characters. This
 * prevents injection of malicious identifiers
 * through the push API.</li>
 * <li><b>Sanitized error messages:</b> Exceptions do not expose internal
 * implementation details.</li>
 * </ul>
 *
 * @see ConfigurationUpdateCallback
 * @see PushablePDPConfigurationSource
 */
public class SinkBasedPDPConfigurationSource implements PushablePDPConfigurationSource {

    private final ConfigurationUpdateCallback callback;
    private final Set<String>                 knownPdpIds = new HashSet<>();
    private final AtomicBoolean               disposed    = new AtomicBoolean(false);

    /**
     * Creates a new sink-based configuration source.
     *
     * @param callback
     * called when a configuration is pushed
     */
    public SinkBasedPDPConfigurationSource(@NonNull ConfigurationUpdateCallback callback) {
        this.callback = callback;
    }

    @Override
    public void pushConfiguration(PDPConfiguration configuration) {
        if (disposed.get()) {
            throw new PDPConfigurationException("Cannot push configuration to disposed source.");
        }

        val pdpId = configuration.pdpId();
        PDPConfigurationSource.validatePdpId(pdpId);

        synchronized (knownPdpIds) {
            knownPdpIds.add(pdpId);
        }

        callback.onConfigurationUpdate(configuration);
    }

    @Override
    public void removeConfiguration(String pdpId) {
        PDPConfigurationSource.validatePdpId(pdpId);
        synchronized (knownPdpIds) {
            knownPdpIds.remove(pdpId);
        }
    }

    /**
     * Returns a snapshot of currently known PDP IDs.
     *
     * @return immutable set of PDP identifiers
     */
    public Set<String> getAvailablePdpIds() {
        synchronized (knownPdpIds) {
            return Set.copyOf(knownPdpIds);
        }
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            synchronized (knownPdpIds) {
                knownPdpIds.clear();
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

}
