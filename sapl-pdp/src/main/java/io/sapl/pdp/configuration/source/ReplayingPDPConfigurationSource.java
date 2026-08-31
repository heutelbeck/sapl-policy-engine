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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Decorator that wraps a {@link io.sapl.pdp.configuration.source.PDPConfigurationSource}
 * and replays the last known configuration event for every {@code pdpId} to each new subscriber.
 * <p>
 * Every {@code PDPConfigurationSource} grants a guarantee to replay all events on the
 * first subscribe. Without this decorator, two independent consumers of the shared
 * source bean {@code PdpVoterSource} and the {@code RoutingAttributeRepository} race
 * for that single slot. Whoever loses the face stays empty until an unrelated file-system
 * change or bundle change happens and fires a fresh event. This class avoids the race
 * problem by caching the event and replaying the event to every subscriber.
 */
@Slf4j
public final class ReplayingPDPConfigurationSource implements PDPConfigurationSource {

    private static final String WARN_LISTENER_THREW = "Configuration listener threw on event {}: {}.";

    // The object that will be decorated
    private final PDPConfigurationSource            delegate;
    private final Map<String, ConfigurationEvent>   lastEventByPdpId = new ConcurrentHashMap<>();
    private final Set<Consumer<ConfigurationEvent>> listeners        = ConcurrentHashMap.newKeySet();

    /**
     * Subscribes to the {@code delegate}, so the replay cache can be filled as soon as this
     * decorator is constructed. This happens before any of it's own subscriber can register
     * themselves to this class.
     *
     * @param delegate the source to wrap.
     */
    public ReplayingPDPConfigurationSource(@NonNull PDPConfigurationSource delegate) {
        this.delegate = delegate;
        delegate.subscribe(this::onRawEvent);
    }

    /**
     * Registers a listener and immediately replays the last known event for
     * every PDP ID, allowing the listener to initialize its state.
     *
     * @param listener The listener to subscribe. Immediately receives a replay of every currently known event.
     */
    @Override
    public void subscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        listeners.add(listener);
        lastEventByPdpId.values().forEach(event -> safeAccept(listener, event));
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener The listener to remove.
     */
    @Override
    public void unsubscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Delegates to the wrapped {@link io.sapl.pdp.configuration.source.PDPConfigurationSource}
     *
     * @return {@code true} if the wrapped source is already closed.
     */
    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /**
     * Closes the wrapped {@link io.sapl.pdp.configuration.source.PDPConfigurationSource}
     */
    @Override
    public void close() throws Exception {
        delegate.close();
    }

    // Saves the event that happened last and removes if it was a removed configuration.
    private void onRawEvent(ConfigurationEvent event) {
        val pdpId = pdpIdOf(event);
        if (event instanceof ConfigurationEvent.ConfigurationRemoved) {
            lastEventByPdpId.remove(pdpId);
        } else {
            lastEventByPdpId.put(pdpId, event);
        }
        listeners.forEach(listener -> safeAccept(listener, event));
    }

    // Ensures that a failed event on one listener does not cause a failure for the others.
    private static void safeAccept(Consumer<ConfigurationEvent> listener, ConfigurationEvent event) {
        try {
            listener.accept(event);
        } catch (Exception e) {
            log.warn(WARN_LISTENER_THREW, event, e.getMessage());
        }
    }

    // Get the PDP id's for every event type.
    private static String pdpIdOf(ConfigurationEvent event) {
        return switch (event) {
        case ConfigurationEvent.NewConfiguration(var configuration)          -> configuration.pdpId();
        case ConfigurationEvent.ConfigurationRemoved(var pdpId)              -> pdpId;
        case ConfigurationEvent.ConfigurationError(var pdpId, var ignored)   -> pdpId;
        case ConfigurationEvent.ConfigurationExpired(var pdpId, var ignored) -> pdpId;
        };
    }
}
