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
package io.sapl.pdp.configuration;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPdpVoter;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.compiler.util.CompilationErrorFormatter;
import io.sapl.legacy.api.attributes.AttributeBroker;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Holds the latest compiled PDP configuration per pdpId and notifies
 * registered listeners when the configuration changes.
 * <p>
 * Two access patterns are supported:
 * <ul>
 * <li><b>Snapshot read</b> via {@link #getCurrentConfiguration(String)} —
 * lock-free volatile read of the current compiled voter from a
 * per-pdpId {@link AtomicReference} cache. This is the fast path for
 * one-shot evaluations.</li>
 * <li><b>Change notification</b> via
 * {@link #subscribeToUpdates(String, Consumer)} — registers a callback
 * invoked synchronously on the loader thread whenever the configuration
 * for a pdpId is replaced or removed. Callers that need a reactive
 * stream compose snapshot + notifications themselves; this class stays
 * reactor-free.</li>
 * </ul>
 * <p>
 * Listener invocation is synchronous and best-effort: an exception
 * thrown by one listener is logged and does not prevent the others from
 * running. Configuration updates are rare (driven by file changes or
 * remote bundle polls), so the simple iterate-and-call pattern is
 * sufficient.
 * <p>
 * Thread-safe. The configuration cache uses {@link AtomicReference} for
 * lock-free reads; the listener registry uses {@link ConcurrentHashMap}
 * with concurrent key sets.
 */
@Slf4j
@RequiredArgsConstructor
public class PdpVoterSource implements AutoCloseable {

    private static final String WARN_CONFIGURATION_REJECTED = "Configuration rejected:\n{}";
    private static final String WARN_LISTENER_THREW         = "Update listener for pdpId '{}' threw: {}";

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Getter
    private final FunctionBroker  functionBroker;
    @Getter
    private final AttributeBroker attributeBroker;
    private final Clock           clock;

    /**
     * Per-PDP configuration cache. Lock-free volatile reads via
     * {@link AtomicReference} provide better performance than
     * {@link ConcurrentHashMap} for single-value access patterns where the
     * key is known.
     */
    private final Map<String, AtomicReference<Optional<CompiledPdpVoter>>> configCache = new ConcurrentHashMap<>();

    /**
     * Per-PDP update listeners. Each set is invoked synchronously on the
     * loader thread when a configuration is loaded or removed for the
     * corresponding pdpId.
     */
    private final Map<String, Set<Consumer<PdpUpdateEvent>>> updateListeners = new ConcurrentHashMap<>();

    /**
     * Per-PDP status tracking for health reporting.
     */
    private final Map<String, AtomicReference<PdpStatus>> statusCache = new ConcurrentHashMap<>();

    /**
     * Loads a new configuration for a PDP, compiling all SAPL documents and
     * making the configuration immediately available to both synchronous
     * and notified readers.
     * <p>
     * On compilation failure:
     * <ul>
     * <li>{@code keepOldConfigOnError == true}: the PDP transitions to
     * STALE (if a valid config exists) or stores an error voter; this
     * method does not throw.</li>
     * <li>{@code keepOldConfigOnError == false}: this method throws
     * {@link PDPConfigurationException} wrapping the compilation error.
     * No state is changed.</li>
     * </ul>
     *
     * @param pdpConfiguration the configuration to load
     * @param keepOldConfigOnError if true, retains existing configuration
     * on compilation errors and does not throw; if false, propagates the
     * compilation error to the caller
     * @throws PDPConfigurationException if {@code keepOldConfigOnError}
     * is false and compilation fails
     */
    public void loadConfiguration(PDPConfiguration pdpConfiguration, boolean keepOldConfigOnError) {
        val compilationContext = new CompilationContext(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(),
                pdpConfiguration.data(), functionBroker);
        compilationContext.setCompilerOptions(pdpConfiguration.compilerOptions());
        CompiledPdpVoter newConfiguration;
        try {
            newConfiguration = PdpCompiler.compilePDPConfiguration(pdpConfiguration, compilationContext);
        } catch (SaplCompilerException compilerException) {
            val formattedError = CompilationErrorFormatter.format(compilerException);
            if (!keepOldConfigOnError) {
                throw new PDPConfigurationException(formattedError, compilerException);
            }
            val now          = clock.instant();
            val currentState = getStatusRef(pdpConfiguration.pdpId()).get().state();
            if (currentState == PdpState.LOADED || currentState == PdpState.STALE) {
                getStatusRef(pdpConfiguration.pdpId()).updateAndGet(current -> new PdpStatus(PdpState.STALE,
                        current.configurationId(), current.combiningAlgorithm(), current.documentCount(),
                        current.lastSuccessfulLoad(), now, formattedError));
            } else {
                val errorVoter = PdpCompiler.createErrorVoter(pdpConfiguration, compilationContext, compilerException);
                getConfigRef(pdpConfiguration.pdpId()).set(Optional.of(errorVoter));
                notifyListeners(pdpConfiguration.pdpId(),
                        new PdpUpdateEvent.Voter(pdpConfiguration.pdpId(), errorVoter));
                getStatusRef(pdpConfiguration.pdpId()).updateAndGet(current -> new PdpStatus(PdpState.ERROR, null, null,
                        0, current.lastSuccessfulLoad(), now, formattedError));
            }
            log.warn(WARN_CONFIGURATION_REJECTED, formattedError);
            return;
        }

        getConfigRef(pdpConfiguration.pdpId()).set(Optional.of(newConfiguration));
        notifyListeners(pdpConfiguration.pdpId(), new PdpUpdateEvent.Voter(pdpConfiguration.pdpId(), newConfiguration));

        val now = clock.instant();
        getStatusRef(pdpConfiguration.pdpId()).set(new PdpStatus(PdpState.LOADED, pdpConfiguration.configurationId(),
                pdpConfiguration.combiningAlgorithm(), pdpConfiguration.saplDocuments().size(), now, null, null));
    }

    /**
     * Dispatches a
     * {@link io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent}
     * to the appropriate operation: {@link ConfigurationEvent.Load} maps
     * to {@link #loadConfiguration(PDPConfiguration, boolean)},
     * {@link ConfigurationEvent.Remove} maps to
     * {@link #removeConfigurationForPdp(String)}.
     * <p>
     * Used as the subscribe-callback bridge between
     * {@link io.sapl.pdp.configuration.source.PDPConfigurationSource}
     * producers and this voter source.
     *
     * @param event the configuration event to dispatch
     * @throws PDPConfigurationException if the event is a
     * {@link ConfigurationEvent.Load} with {@code keepOldOnError == false}
     * and the configuration fails to compile
     */
    public void handle(ConfigurationEvent event) {
        switch (event) {
        case ConfigurationEvent.Load(var configuration, var keepOldOnError) ->
            loadConfiguration(configuration, keepOldOnError);
        case ConfigurationEvent.Remove(var pdpId)                           -> removeConfigurationForPdp(pdpId);
        }
    }

    /**
     * Removes the configuration for a PDP, dispatching a
     * {@link PdpUpdateEvent.Removed} to all listeners and removing the
     * status entry.
     *
     * @param pdpId the PDP identifier
     */
    public void removeConfigurationForPdp(String pdpId) {
        getConfigRef(pdpId).set(Optional.empty());
        notifyListeners(pdpId, new PdpUpdateEvent.Removed(pdpId));
        statusCache.remove(pdpId);
    }

    /**
     * Returns the current configuration synchronously. Lock-free volatile
     * read from {@link AtomicReference}.
     *
     * @param pdpId the PDP identifier
     * @return the current configuration, or empty if none is loaded
     */
    public Optional<CompiledPdpVoter> getCurrentConfiguration(String pdpId) {
        return getConfigRef(pdpId).get();
    }

    /**
     * Registers a listener that is invoked synchronously whenever the
     * configuration for the given pdpId changes. The listener receives a
     * {@link PdpUpdateEvent.Voter} for loads (including error voters when
     * {@code keepOldConfigOnError} was set) and a
     * {@link PdpUpdateEvent.Removed} when the configuration is removed.
     * <p>
     * The listener is <b>not</b> invoked with the current value at the
     * time of subscription. Callers that want the current snapshot should
     * call {@link #getCurrentConfiguration(String)} themselves; combining
     * the two gives "snapshot plus updates" semantics. Subscribing before
     * reading the snapshot is the safer order: it cannot lose an update
     * emitted between the two calls.
     * <p>
     * Multiple subscriptions of the same listener instance are
     * deduplicated. Subscribing to a closed voter source is a no-op.
     *
     * @param pdpId the PDP identifier
     * @param listener the callback to invoke on configuration change
     */
    public void subscribeToUpdates(String pdpId, Consumer<PdpUpdateEvent> listener) {
        if (closed.get()) {
            return;
        }
        updateListeners.computeIfAbsent(pdpId, id -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    /**
     * Removes a listener previously registered via
     * {@link #subscribeToUpdates(String, Consumer)}. Calls for an unknown
     * pdpId or unknown listener are silently ignored.
     *
     * @param pdpId the PDP identifier
     * @param listener the previously registered callback
     */
    public void unsubscribeFromUpdates(String pdpId, Consumer<PdpUpdateEvent> listener) {
        val set = updateListeners.get(pdpId);
        if (set != null) {
            set.remove(listener);
        }
    }

    /**
     * Returns the current status of all known PDPs.
     *
     * @return an unmodifiable map of PDP ID to status
     */
    public Map<String, PdpStatus> getAllPdpStatuses() {
        val result = new ConcurrentHashMap<String, PdpStatus>();
        for (val entry : statusCache.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return Map.copyOf(result);
    }

    /**
     * Returns the current status of a specific PDP.
     *
     * @param pdpId the PDP identifier
     * @return the current status, or empty if the PDP is unknown
     */
    public Optional<PdpStatus> getPdpStatus(String pdpId) {
        val ref = statusCache.get(pdpId);
        return ref != null ? Optional.of(ref.get()) : Optional.empty();
    }

    /**
     * Releases all resources: clears all listeners and the per-PDP caches.
     * Idempotent. Safe to call from container shutdown hooks even if
     * {@link AutoCloseable#close()} has already been invoked.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        updateListeners.clear();
        configCache.clear();
        statusCache.clear();
    }

    private void notifyListeners(String pdpId, PdpUpdateEvent event) {
        val set = updateListeners.get(pdpId);
        if (set == null) {
            return;
        }
        for (val listener : set) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn(WARN_LISTENER_THREW, pdpId, e.getMessage());
            }
        }
    }

    private AtomicReference<Optional<CompiledPdpVoter>> getConfigRef(String pdpId) {
        return configCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(Optional.empty()));
    }

    private AtomicReference<PdpStatus> getStatusRef(String pdpId) {
        return statusCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(PdpStatus.initial()));
    }

}
