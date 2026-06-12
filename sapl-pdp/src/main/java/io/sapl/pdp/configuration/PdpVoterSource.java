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

import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.compiler.util.CompilationErrorFormatter;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Holds the latest compiled PDP configuration per pdpId and notifies
 * registered listeners when the configuration changes.
 * <p>
 * Two event streams drive recompiling: configuration changes from
 * {@link io.sapl.pdp.configuration.source.PDPConfigurationSource} and
 * plugin snapshots from {@link PluginsSource}. Both go through a
 * single {@link ReentrantLock}; concurrent producer events serialize
 * deterministically. No configuration is published until the plugins
 * source has delivered at least one snapshot.
 * <p>
 * Snapshot reads on {@link #getCurrentConfiguration(String)} stay
 * lock-free via per-pdpId {@link AtomicReference} caches.
 */
@Slf4j
public class PdpVoterSource implements AutoCloseable {

    private static final String WARN_CONFIGURATION_REJECTED = "Configuration rejected:\n{}";
    private static final String WARN_DEFERRED_CONFIGURATION = "Configuration for pdpId '{}' arrived before plugins source delivered an initial snapshot. Retained and will compile when plugins are available.";
    private static final String WARN_LISTENER_THREW         = "Update listener for pdpId '{}' threw: {}";

    private final Clock         clock;
    private final PluginsSource pluginsSource;

    private final ReentrantLock           stateLock      = new ReentrantLock();
    private final Consumer<PluginsBundle> pluginListener = this::onPluginsSnapshot;
    private volatile PluginsBundle        currentPlugins;
    private volatile boolean              closed         = false;

    /**
     * Per-pdpId retained source configuration. Recompile triggered by a
     * plugins snapshot reuses the last successfully-loaded
     * {@link PDPConfiguration} for the affected pdpId. Removed when the
     * configuration source emits a {@link ConfigurationEvent.Remove}.
     * Mutated only under {@link #stateLock}.
     */
    private final Map<String, PDPConfiguration> retainedConfigurations = new HashMap<>();

    /**
     * Per-PDP configuration cache. Lock-free volatile reads via
     * {@link AtomicReference} for snapshot consumers.
     */
    private final Map<String, AtomicReference<Optional<CompiledPdp>>> configCache = new ConcurrentHashMap<>();

    /**
     * Per-PDP update listeners.
     */
    private final Map<String, Set<Consumer<PdpUpdateEvent>>> updateListeners = new ConcurrentHashMap<>();

    /**
     * Per-PDP status tracking for health reporting.
     */
    private final Map<String, AtomicReference<PdpStatus>> statusCache = new ConcurrentHashMap<>();

    public PdpVoterSource(PluginsSource pluginsSource, Clock clock) {
        this.clock         = clock;
        this.pluginsSource = pluginsSource;
        pluginsSource.subscribe(pluginListener);
    }

    /**
     * @return the plugins bundle currently in effect, or {@code null}
     * if the plugins source has not delivered a snapshot yet
     */
    public PluginsBundle getPlugins() {
        return currentPlugins;
    }

    private void onPluginsSnapshot(PluginsBundle newPlugins) {
        stateLock.lock();
        try {
            currentPlugins = newPlugins;
            for (val entry : retainedConfigurations.entrySet()) {
                try {
                    loadConfigurationLocked(entry.getValue(), true, newPlugins);
                } catch (Exception e) {
                    log.warn("Recompile of pdpId '{}' after plugins push threw: {}", entry.getKey(), e.getMessage());
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

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
     * If the plugins source has not yet delivered an initial snapshot,
     * the configuration is retained and a warning is logged. It will
     * compile automatically when the plugins source delivers its first
     * snapshot. In this deferred case, {@code keepOldConfigOnError} is
     * implicitly treated as {@code true} because synchronous validation
     * is impossible without a broker.
     *
     * @param pdpConfiguration the configuration to load
     * @param keepOldConfigOnError if true, retains existing configuration
     * on compilation errors and does not throw; if false, propagates the
     * compilation error to the caller
     * @throws PDPConfigurationException if {@code keepOldConfigOnError}
     * is false and synchronous compilation fails
     */
    public void loadConfiguration(PDPConfiguration pdpConfiguration, boolean keepOldConfigOnError) {
        stateLock.lock();
        try {
            val plugins = currentPlugins;
            if (plugins == null) {
                retainedConfigurations.put(pdpConfiguration.pdpId(), pdpConfiguration);
                getStatusRef(pdpConfiguration.pdpId()).set(new PdpStatus(PdpState.AWAITING_PLUGINS,
                        pdpConfiguration.configurationId(), pdpConfiguration.combiningAlgorithm(),
                        pdpConfiguration.saplDocuments().size(), null, null, null));
                log.warn(WARN_DEFERRED_CONFIGURATION, pdpConfiguration.pdpId());
                return;
            }
            loadConfigurationLocked(pdpConfiguration, keepOldConfigOnError, plugins);
        } finally {
            stateLock.unlock();
        }
    }

    private void loadConfigurationLocked(PDPConfiguration pdpConfiguration, boolean keepOldConfigOnError,
            PluginsBundle plugins) {
        val priorRetained      = retainedConfigurations.put(pdpConfiguration.pdpId(), pdpConfiguration);
        val compilationContext = new CompilationContext(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(),
                pdpConfiguration.data(), plugins.functionBroker());
        compilationContext.setCompilerOptions(pdpConfiguration.compilerOptions());
        CompiledPdp newConfiguration;
        try {
            newConfiguration = PdpCompiler.compilePDPConfiguration(pdpConfiguration, compilationContext, plugins);
        } catch (SaplCompilerException compilerException) {
            val formattedError = CompilationErrorFormatter.format(compilerException);
            if (!keepOldConfigOnError) {
                // Fail-fast must leave state unchanged: restore the previously
                // retained configuration so the rejected one is not retained.
                if (priorRetained == null) {
                    retainedConfigurations.remove(pdpConfiguration.pdpId());
                } else {
                    retainedConfigurations.put(pdpConfiguration.pdpId(), priorRetained);
                }
                throw new PDPConfigurationException(formattedError, compilerException);
            }
            val now          = clock.instant();
            val currentState = getStatusRef(pdpConfiguration.pdpId()).get().state();
            if (currentState == PdpState.LOADED || currentState == PdpState.STALE) {
                getStatusRef(pdpConfiguration.pdpId()).updateAndGet(current -> new PdpStatus(PdpState.STALE,
                        current.configurationId(), current.combiningAlgorithm(), current.documentCount(),
                        current.lastSuccessfulLoad(), now, formattedError));
            } else {
                val errorVoter = PdpCompiler.createErrorVoter(pdpConfiguration, compilerException, plugins);
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
     * to the appropriate operation.
     *
     * @param event the configuration event to dispatch
     */
    public void handle(ConfigurationEvent event) {
        switch (event) {
        case ConfigurationEvent.Load(var configuration, var keepOldOnError) ->
            loadConfiguration(configuration, keepOldOnError);
        case ConfigurationEvent.Remove(var pdpId)                           -> removeConfigurationForPdp(pdpId);
        }
    }

    /**
     * Removes the configuration for a PDP.
     *
     * @param pdpId the PDP identifier
     */
    public void removeConfigurationForPdp(String pdpId) {
        stateLock.lock();
        try {
            retainedConfigurations.remove(pdpId);
            getConfigRef(pdpId).set(Optional.empty());
            notifyListeners(pdpId, new PdpUpdateEvent.Removed(pdpId));
            statusCache.remove(pdpId);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Returns the current configuration synchronously. Lock-free volatile
     * read from {@link AtomicReference}.
     *
     * @param pdpId the PDP identifier
     * @return the current configuration, or empty if none is loaded
     */
    public Optional<CompiledPdp> getCurrentConfiguration(String pdpId) {
        return getConfigRef(pdpId).get();
    }

    /**
     * Registers a listener that is invoked synchronously whenever the
     * configuration for the given pdpId changes.
     * <p>
     * On subscription the listener is delivered the current state once, under
     * the source lock, so the initial value and any concurrent update arrive in
     * order and no stale configuration can latch downstream. The current state
     * is a {@link PdpUpdateEvent.Voter} when a configuration is loaded, or a
     * {@link PdpUpdateEvent.Removed} when none is. Because the event type has no
     * dedicated "absent" case, {@code Removed} on this initial delivery means
     * "no current configuration" rather than "a configuration was deleted";
     * both map to a fail-closed no-config decision downstream.
     *
     * @param pdpId the PDP identifier
     * @param listener the callback to invoke on configuration change
     */
    public void subscribeToUpdates(String pdpId, Consumer<PdpUpdateEvent> listener) {
        stateLock.lock();
        try {
            if (closed) {
                return;
            }
            updateListeners.computeIfAbsent(pdpId, id -> ConcurrentHashMap.newKeySet()).add(listener);
            // Deliver the current configuration under the lock so a concurrent
            // update cannot interleave between subscription and this initial
            // delivery and leave a stale configuration latched downstream.
            val current = getConfigRef(pdpId).get();
            val initial = current.<PdpUpdateEvent>map(voter -> new PdpUpdateEvent.Voter(pdpId, voter))
                    .orElseGet(() -> new PdpUpdateEvent.Removed(pdpId));
            notifyListener(listener, pdpId, initial);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Removes a previously registered listener.
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
     * Releases all resources.
     */
    @Override
    public void close() {
        stateLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            pluginsSource.unsubscribe(pluginListener);
            updateListeners.clear();
            configCache.clear();
            statusCache.clear();
            retainedConfigurations.clear();
        } finally {
            stateLock.unlock();
        }
    }

    private void notifyListeners(String pdpId, PdpUpdateEvent event) {
        val set = updateListeners.get(pdpId);
        if (set == null) {
            return;
        }
        for (val listener : set) {
            notifyListener(listener, pdpId, event);
        }
    }

    private void notifyListener(Consumer<PdpUpdateEvent> listener, String pdpId, PdpUpdateEvent event) {
        try {
            listener.accept(event);
        } catch (Exception e) {
            log.warn(WARN_LISTENER_THREW, pdpId, e.getMessage());
        }
    }

    private AtomicReference<Optional<CompiledPdp>> getConfigRef(String pdpId) {
        return configCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(Optional.empty()));
    }

    private AtomicReference<PdpStatus> getStatusRef(String pdpId) {
        return statusCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(PdpStatus.initial()));
    }

}
