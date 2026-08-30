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
package io.sapl.pdp.configuration.source;

import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Wraps another {@link PDPConfigurationSource} and replays the last known
 * event for every pdpId to each new subscriber, not only the first one. The
 * wrapped source activates and starts monitoring exactly once - this
 * decorator is its sole subscriber - so every listener registered here sees
 * at least the most recently emitted state immediately upon subscribing,
 * regardless of registration order.
 */
@Slf4j
public final class ReplayingPDPConfigurationSource implements PDPConfigurationSource {

    private static final String WARN_LISTENER_THREW = "Configuration listener threw on event {}: {}.";

    private final PDPConfigurationSource delegate;

    private final Map<String, ConfigurationEvent>   lastEventByPdpId = new ConcurrentHashMap<>();
    private final Set<Consumer<ConfigurationEvent>> listeners        = ConcurrentHashMap.newKeySet();

    public ReplayingPDPConfigurationSource(@NonNull PDPConfigurationSource delegate) {
        this.delegate = delegate;
        delegate.subscribe(this::onRawEvent);
    }

    @Override
    public void subscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        listeners.add(listener);
        lastEventByPdpId.values().forEach(event -> safeAccept(listener, event));
    }

    @Override
    public void unsubscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    private void onRawEvent(ConfigurationEvent event) {
        val pdpId = pdpIdOf(event);
        if (event instanceof ConfigurationEvent.ConfigurationRemoved) {
            lastEventByPdpId.remove(pdpId);
        } else {
            lastEventByPdpId.put(pdpId, event);
        }
        listeners.forEach(listener -> safeAccept(listener, event));
    }

    private static void safeAccept(Consumer<ConfigurationEvent> listener, ConfigurationEvent event) {
        try {
            listener.accept(event);
        } catch (Exception e) {
            // Isolate listeners: a throwing one must not skip the others or
            // escape onto the delegate's monitoring thread.
            log.warn(WARN_LISTENER_THREW, event, e.getMessage());
        }
    }

    private static String pdpIdOf(ConfigurationEvent event) {
        return switch (event) {
        case ConfigurationEvent.NewConfiguration(var configuration)          -> configuration.pdpId();
        case ConfigurationEvent.ConfigurationRemoved(var pdpId)              -> pdpId;
        case ConfigurationEvent.ConfigurationError(var pdpId, var ignored)   -> pdpId;
        case ConfigurationEvent.ConfigurationExpired(var pdpId, var ignored) -> pdpId;
        };
    }
}
