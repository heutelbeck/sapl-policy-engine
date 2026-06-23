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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static final String ERROR_RECOMPILE_FAILED      = "Recompilation failed: %s";
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
     * Per-pdpId most recently submitted source configuration. A plugins snapshot
     * recompiles this retained config for the affected pdpId; the served voter
     * remains the last successfully-compiled one until a recompile succeeds.
     * Removed when the configuration source emits a
     * {@link ConfigurationEvent.Remove}. Mutated only under {@link #stateLock}.
     */
    private final Map<String, PDPConfiguration> retainedConfigurations = new HashMap<>();

    /**
     * Monotonic plugins-snapshot counter, bumped under {@link #stateLock} on each
     * snapshot. Paired with {@link #servedGeneration} so an incremental migration
     * can skip a pdpId a concurrent config update already brought current.
     */
    private long pluginGeneration = 0;

    /**
     * Per-pdpId plugins generation the served voter was compiled against. Lets the
     * per-pdpId migration triggered by a snapshot skip pdpIds already migrated to
     * (or past) the snapshot's generation. Mutated only under {@link #stateLock}.
     */
    private final Map<String, Long> servedGeneration = new HashMap<>();

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
        long         targetGeneration;
        List<String> pdpIds;
        stateLock.lock();
        try {
            currentPlugins   = newPlugins;
            targetGeneration = ++pluginGeneration;
            pdpIds           = new ArrayList<>(retainedConfigurations.keySet());
        } finally {
            stateLock.unlock();
        }
        // Migrate each pdpId under a short lock hold, releasing the lock between them
        // so a
        // config update is blocked for at most one pdpId rather than the whole
        // snapshot.
        // pdpIds are independent decision domains; eventual consistency across them is
        // fine.
        for (val pdpId : pdpIds) {
            migratePdpId(pdpId, targetGeneration);
        }
    }

    private void migratePdpId(String pdpId, long targetGeneration) {
        stateLock.lock();
        try {
            if (closed || servedGeneration.getOrDefault(pdpId, Long.MIN_VALUE) >= targetGeneration) {
                // A concurrent config update already brought this pdpId to the target
                // generation, or the source is closing.
                return;
            }
            val config  = retainedConfigurations.get(pdpId);
            val plugins = currentPlugins;
            if (config == null || plugins == null) {
                return;
            }
            try {
                // A plugin swap changes the compile-time runtime. A voter compiled against
                // the retired bundle must not keep being served, so a recompile failure
                // fails closed to an error voter rather than retaining the old one (STALE).
                // keepOld semantics are meaningful only for config updates, where the
                // runtime is unchanged.
                loadConfigurationLocked(config, OnCompileError.FAIL_CLOSED, plugins);
                // Stamp the generation actually compiled against. This is the live
                // pluginGeneration (the generation of currentPlugins read above), which under
                // a concurrent newer snapshot may exceed targetGeneration; both were read
                // under this single lock hold, so they are consistent.
                servedGeneration.put(pdpId, pluginGeneration);
            } catch (Exception e) {
                log.warn("Recompile of pdpId '{}' after plugins push threw: {}", pdpId, e.getMessage());
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
            loadConfigurationLocked(pdpConfiguration,
                    keepOldConfigOnError ? OnCompileError.RETAIN_LAST_GOOD : OnCompileError.THROW, plugins);
            // Reached only when compilation did not fail fast: this pdpId is now current
            // for the live plugins generation, so an in-flight snapshot migration skips it.
            servedGeneration.put(pdpConfiguration.pdpId(), pluginGeneration);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * How {@link #loadConfigurationLocked} reacts to a compilation failure.
     * {@code THROW} and {@code RETAIN_LAST_GOOD} are the two config-update modes
     * (the runtime is unchanged); {@code FAIL_CLOSED} is the plugin-swap mode
     * (the runtime changed, so the last-good voter is bound to a retired bundle
     * and cannot be retained).
     */
    private enum OnCompileError {
        THROW,
        RETAIN_LAST_GOOD,
        FAIL_CLOSED
    }

    private void loadConfigurationLocked(PDPConfiguration pdpConfiguration, OnCompileError onCompileError,
            PluginsBundle plugins) {
        val priorRetained      = retainedConfigurations.put(pdpConfiguration.pdpId(), pdpConfiguration);
        val compilationContext = new CompilationContext(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(),
                pdpConfiguration.data(), plugins.functionBroker());
        compilationContext.setCompilerOptions(pdpConfiguration.compilerOptions());
        CompiledPdp newConfiguration;
        try {
            newConfiguration = PdpCompiler.compilePDPConfiguration(pdpConfiguration, compilationContext, plugins);
        } catch (SaplCompilerException compilerException) {
            handleCompileFailure(pdpConfiguration, onCompileError, plugins, priorRetained, compilerException);
            return;
        } catch (RuntimeException unexpected) {
            // A compile-time hook (e.g. a plugin function broker invoked during constant
            // folding) violated the never-throw contract. Treat any compile fault as a
            // compile failure so the swap path fails closed rather than stranding a voter
            // bound to the retired bundle.
            handleCompileFailure(pdpConfiguration, onCompileError, plugins, priorRetained,
                    new SaplCompilerException(ERROR_RECOMPILE_FAILED.formatted(unexpected.getMessage()), unexpected));
            return;
        }

        getConfigRef(pdpConfiguration.pdpId()).set(Optional.of(newConfiguration));
        notifyListeners(pdpConfiguration.pdpId(), new PdpUpdateEvent.Voter(pdpConfiguration.pdpId(), newConfiguration));

        val now = clock.instant();
        getStatusRef(pdpConfiguration.pdpId()).set(new PdpStatus(PdpState.LOADED, pdpConfiguration.configurationId(),
                pdpConfiguration.combiningAlgorithm(), pdpConfiguration.saplDocuments().size(), now, null, null));
    }

    private void handleCompileFailure(PDPConfiguration pdpConfiguration, OnCompileError onCompileError,
            PluginsBundle plugins, PDPConfiguration priorRetained, SaplCompilerException compilerException) {
        val formattedError = CompilationErrorFormatter.format(compilerException);
        if (onCompileError == OnCompileError.THROW) {
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
        // Keep serving the last-good voter (STALE) only on a config update, where that
        // voter is still bound to the live runtime. A plugin swap (FAIL_CLOSED) must
        // fail closed instead: the last-good voter is bound to the retired bundle.
        if (onCompileError == OnCompileError.RETAIN_LAST_GOOD
                && (currentState == PdpState.LOADED || currentState == PdpState.STALE)) {
            getStatusRef(pdpConfiguration.pdpId()).updateAndGet(
                    current -> new PdpStatus(PdpState.STALE, current.configurationId(), current.combiningAlgorithm(),
                            current.documentCount(), current.lastSuccessfulLoad(), now, formattedError));
        } else {
            val errorVoter = PdpCompiler.createErrorVoter(pdpConfiguration, compilerException, plugins);
            getConfigRef(pdpConfiguration.pdpId()).set(Optional.of(errorVoter));
            notifyListeners(pdpConfiguration.pdpId(), new PdpUpdateEvent.Voter(pdpConfiguration.pdpId(), errorVoter));
            getStatusRef(pdpConfiguration.pdpId()).updateAndGet(current -> new PdpStatus(PdpState.ERROR, null, null, 0,
                    current.lastSuccessfulLoad(), now, formattedError));
        }
        log.warn(WARN_CONFIGURATION_REJECTED, formattedError);
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
            servedGeneration.remove(pdpId);
            getConfigRef(pdpId).set(Optional.empty());
            notifyListeners(pdpId, new PdpUpdateEvent.Removed(pdpId));
            statusCache.remove(pdpId);
            configCache.remove(pdpId);
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
        // Non-inserting read: a query for an unknown pdpId must not create a cache
        // entry,
        // otherwise configCache grows without bound as distinct pdpIds are read over
        // the
        // PDP's lifetime (tenant churn, removed or transient pdpIds).
        val ref = configCache.get(pdpId);
        return ref != null ? ref.get() : Optional.empty();
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
        stateLock.lock();
        try {
            val set = updateListeners.get(pdpId);
            if (set != null) {
                set.remove(listener);
                // Prune the entry when the last live subscriber leaves, so
                // transient pdpIds do not leak. Config removal keeps live
                // listeners, so this is the only place that evicts them.
                if (set.isEmpty()) {
                    updateListeners.remove(pdpId);
                }
            }
        } finally {
            stateLock.unlock();
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
            servedGeneration.clear();
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
