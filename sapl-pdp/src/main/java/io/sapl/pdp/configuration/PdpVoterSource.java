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

import static io.sapl.pdp.SaplBuildInfo.version;
import static java.util.List.copyOf;
import static org.semver4j.Semver.parse;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.compiler.util.CompilationErrorFormatter;
import io.sapl.pdp.configuration.PreparationResult.Rejected;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Holds the latest compiled PDP configuration per pdpId and notifies
 * registered listeners when the configuration changes.
 * <p>
 * Two event streams drive recompiling: configuration changes from
 * {@link io.sapl.pdp.configuration.source.PDPConfigurationSource} and
 * plugin snapshots from {@link PluginsSource}. Both go through a
 * single {@link ReentrantLock}. Nested callback requests are queued on their
 * requesting thread until the current transition,
 * including its extension commit, completes. No configuration is published until the plugins
 * source has delivered at least one snapshot.
 * <p>
 * Snapshot reads on {@link #getCurrentConfiguration(String)} stay
 * lock-free via per-pdpId {@link AtomicReference} caches.
 */
@Slf4j
public class PdpVoterSource implements AutoCloseable {

    private static final String ERROR_CONFIGURATION_REJECTED                = "Configuration '%s' for pdpId '%s' was rejected. %s";
    private static final String ERROR_ENGINE_VERSION_INVALID                = "Cannot validate requiredEngineVersion '%s' because the running sapl-pdp build version '%s' is not a valid semantic version. Check the engine build metadata.";
    private static final String ERROR_ENGINE_VERSION_MISMATCH               = "The running sapl-pdp version '%s' does not satisfy requiredEngineVersion '%s'. Deploy this configuration on an engine within the required range.";
    private static final String ERROR_EXCEPTION_MESSAGE_MISSING             = "No exception message was provided.";
    private static final String ERROR_EXTENSIONS_PREPARATION_FAILED         = "Extension preparation failed with %s: %s";
    private static final String ERROR_EXTENSIONS_PREPARATION_RESULT_MISSING = "The extension processor for pdpId '%s' returned no preparation result.";
    private static final String ERROR_RECOMPILE_FAILED                      = "Recompilation failed: %s";
    private static final String ERROR_UNEXPECTED_CONFIGURATION_FAILURE      = "Configuration update failed with %s";
    private static final String WARN_CONFIGURATION_EXPIRED                  = "Configuration expired, failing closed:\n{}";
    private static final String WARN_CONFIGURATION_REJECTED                 = "Configuration rejected:\n{}";
    private static final String WARN_CONFIGURATION_SOURCE_CLOSED            = "Ignoring {} because the PDP configuration source is closed.";
    private static final String WARN_DEFERRED_CONFIGURATION                 = "Configuration for pdpId '{}' arrived before plugins source delivered an initial snapshot. Retained and will compile when plugins are available.";
    private static final String WARN_EXTENSION_COMMIT_FAILED                = "Extension commit for configuration '{}' of pdpId '{}' failed with {}";
    private static final String WARN_EXTENSION_REMOVAL_FAILED               = "Extension remove for pdpId '{}' failed with {}";
    private static final String WARN_LISTENER_FAILED                        = "Update listener for pdpId '{}' failed while processing {} with {}";
    private static final String WARN_PLUGIN_UNSUBSCRIBE_FAILED              = "Plugin unsubscribe failed with {}. Closing the remaining PDP resources.";
    private static final String WARN_STATUS_TIMESTAMP_UNAVAILABLE           = "Cannot read the status timestamp for pdpId '{}'. Recording the status without a timestamp.";

    private final Clock               clock;
    private final PluginsSource       pluginsSource;
    private final ExtensionsProcessor extensionsProcessor;

    private final ReentrantLock            stateLock      = new ReentrantLock();
    private final ConfigurationUpdateQueue updates        = new ConfigurationUpdateQueue(stateLock);
    private final Consumer<PluginsBundle>  pluginListener = this::onPluginsChanged;
    private volatile PluginsBundle         currentPlugins;
    private volatile boolean               closed         = false;

    /**
     * Per-pdpId most recently submitted source configuration. A plugins snapshot
     * recompiles this retained config for the affected pdpId. The served voter
     * remains the last successfully-compiled one until a recompile succeeds.
     * Removed when the configuration source emits a
     * {@link ConfigurationEvent.ConfigurationRemoved}. Mutated only under {@link #stateLock}.
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
        this(pluginsSource, clock, new EmptyExtensionsProcessor());
    }

    public PdpVoterSource(PluginsSource pluginsSource, Clock clock, ExtensionsProcessor extensionsProcessor) {
        this.clock               = clock;
        this.pluginsSource       = pluginsSource;
        this.extensionsProcessor = extensionsProcessor;
        pluginsSource.subscribe(pluginListener);
    }

    /**
     * @return the plugins bundle currently in effect, or {@code null}
     * if the plugins source has not delivered a snapshot yet
     */
    public PluginsBundle getPlugins() {
        return currentPlugins;
    }

    private void onPluginsChanged(PluginsBundle newPlugins) {
        updates.executeSteps(() -> "apply a plugin update", () -> applyPlugins(newPlugins));
    }

    /** Call under {@link #stateLock}. */
    private ConfigurationUpdateQueue.@Nullable Step applyPlugins(PluginsBundle newPlugins) {
        if (ignoreWhenClosed(() -> "apply a plugin update")) {
            return null;
        }
        currentPlugins = newPlugins;
        val targetGeneration = ++pluginGeneration;
        val pdpIds           = new ArrayList<>(retainedConfigurations.keySet());
        return remainingMigrations(pdpIds.iterator(), targetGeneration);
    }

    private ConfigurationUpdateQueue.@Nullable Step remainingMigrations(Iterator<String> pdpIds,
            long targetGeneration) {
        if (!pdpIds.hasNext()) {
            return null;
        }
        val pdpId = pdpIds.next();
        return () -> {
            migratePdpId(pdpId, targetGeneration);
            return remainingMigrations(pdpIds, targetGeneration);
        };
    }

    /** Call under {@link #stateLock}. */
    private void migratePdpId(String pdpId, long targetGeneration) {
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
        val wasLoaded = getStatusRef(pdpId).get().state() == PdpState.LOADED;
        loadConfigurationLocked(config, OnCompileError.FAIL_CLOSED, plugins, !wasLoaded);
    }

    /**
     * Loads a new configuration for a PDP, compiling all SAPL documents and
     * publishing it to synchronous readers and update listeners.
     * Engine version requirements are checked before extension processing or
     * retaining the configuration. A rejected requirement keeps the last good
     * configuration serving and marks it STALE, or marks the PDP ERROR if none exists.
     * <p>
     * On compilation failure the last successfully compiled voter keeps serving and
     * the PDP transitions to STALE when such a voter exists; otherwise an error
     * voter is stored and the PDP transitions to ERROR. This method never throws on
     * a compilation failure, so a caller that needs fail-fast behaviour inspects
     * {@link #getPdpStatus(String)} afterwards.
     * <p>
     * If the plugins source has not yet delivered an initial snapshot, the
     * configuration is retained and a warning is logged. It compiles automatically
     * when the plugins source delivers its first snapshot.
     * <p>
     * A load requested while this thread processes another source transition is
     * queued and returns before activation. This includes update listeners,
     * initial subscription delivery and extension callbacks. It runs after the
     * current transition and extension commit. Other threads execute their own
     * loads after acquiring the source lock.
     *
     * @param pdpConfiguration the configuration to load
     */
    public void loadConfiguration(PDPConfiguration pdpConfiguration) {
        updates.execute(() -> "load configuration '%s' for pdpId '%s'".formatted(pdpConfiguration.configurationId(),
                pdpConfiguration.pdpId()), () -> loadConfigurationNow(pdpConfiguration));
    }

    /** Call under {@link #stateLock}. */
    private void loadConfigurationNow(PDPConfiguration pdpConfiguration) {
        if (ignoreWhenClosed(() -> "load configuration '%s' for pdpId '%s'"
                .formatted(pdpConfiguration.configurationId(), pdpConfiguration.pdpId()))) {
            return;
        }
        val pdpId        = pdpConfiguration.pdpId();
        val versionError = engineVersionError(version(), pdpConfiguration.requiredEngineVersion());
        if (versionError != null) {
            reportConfigurationErrorLocked(pdpId,
                    ERROR_CONFIGURATION_REJECTED.formatted(pdpConfiguration.configurationId(), pdpId, versionError));
            return;
        }
        // The configuration goes live only if the runtime environment accepts its
        // extension slice. A rejection keeps the last-good configuration and compiles
        // and swaps nothing.
        if (prepareExtensions(pdpId, pdpConfiguration) instanceof Rejected(var reason)) {
            reportConfigurationErrorLocked(pdpId,
                    ERROR_CONFIGURATION_REJECTED.formatted(pdpConfiguration.configurationId(), pdpId, reason));
            return;
        }
        val plugins = currentPlugins;
        if (plugins == null) {
            retainedConfigurations.put(pdpId, pdpConfiguration);
            getStatusRef(pdpId).set(new PdpStatus(PdpState.AWAITING_PLUGINS, pdpConfiguration.configurationId(),
                    pdpConfiguration.combiningAlgorithm(), pdpConfiguration.saplDocuments().size(), null, null, null));
            log.warn(WARN_DEFERRED_CONFIGURATION, pdpId);
            return;
        }
        loadConfigurationLocked(pdpConfiguration, OnCompileError.RETAIN_LAST_GOOD, plugins, true);
    }

    static @Nullable String engineVersionError(String engineVersion, @Nullable String requirement) {
        if (requirement == null) {
            return null;
        }
        val parsedVersion = parse(engineVersion);
        if (parsedVersion == null) {
            return ERROR_ENGINE_VERSION_INVALID.formatted(requirement, engineVersion);
        }
        if (!parsedVersion.satisfies(requirement)) {
            return ERROR_ENGINE_VERSION_MISMATCH.formatted(engineVersion, requirement);
        }
        return null;
    }

    /**
     * How {@link #loadConfigurationLocked} reacts to a compilation failure.
     * {@code RETAIN_LAST_GOOD} is the config-update mode (the runtime is unchanged,
     * so the last-good voter can keep serving); {@code FAIL_CLOSED} is the
     * plugin-swap mode (the runtime changed, so the last-good voter is bound to a
     * retired bundle and cannot be retained).
     */
    private enum OnCompileError {
        RETAIN_LAST_GOOD,
        FAIL_CLOSED
    }

    private record ServedState(Optional<CompiledPdp> configuration, PdpStatus status, @Nullable Long generation) {}

    /** Call under {@link #stateLock}. */
    private void loadConfigurationLocked(PDPConfiguration pdpConfiguration, OnCompileError onCompileError,
            PluginsBundle plugins, boolean deployExtensions) {
        val pdpId    = pdpConfiguration.pdpId();
        val previous = new ServedState(getCurrentConfiguration(pdpId), getStatusRef(pdpId).get(),
                servedGeneration.get(pdpId));
        retainedConfigurations.put(pdpId, pdpConfiguration);
        try {
            compileConfigurationLocked(pdpConfiguration, onCompileError, plugins);
            if (deployExtensions && getStatusRef(pdpId).get().state() == PdpState.LOADED) {
                commitExtensions(pdpId, pdpConfiguration);
            }
        } catch (RuntimeException failure) {
            recordUnexpectedFailure(pdpConfiguration, onCompileError, plugins, previous, failure);
            if (onCompileError == OnCompileError.RETAIN_LAST_GOOD) {
                throw failure;
            }
        } catch (Error failure) {
            recordUnexpectedFailure(pdpConfiguration, onCompileError, plugins, previous, failure);
            throw failure;
        }
    }

    /** Call under {@link #stateLock}. */
    private void compileConfigurationLocked(PDPConfiguration pdpConfiguration, OnCompileError onCompileError,
            PluginsBundle plugins) {
        val compilationContext = new CompilationContext(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(),
                pdpConfiguration.data(), plugins.functionBroker());
        compilationContext.setCompilerOptions(pdpConfiguration.compilerOptions());
        CompiledPdp newConfiguration;
        try {
            newConfiguration = PdpCompiler.compilePDPConfiguration(pdpConfiguration, compilationContext, plugins);
        } catch (SaplCompilerException compilerException) {
            handleCompileFailure(pdpConfiguration, onCompileError, plugins, compilerException);
            return;
        } catch (RuntimeException unexpected) {
            // A compile-time hook (e.g. a plugin function broker invoked during constant
            // folding) violated the never-throw contract. Treat any compile fault as a
            // compile failure so the swap path fails closed rather than stranding a voter
            // bound to the retired bundle.
            handleCompileFailure(pdpConfiguration, onCompileError, plugins, new SaplCompilerException(
                    ERROR_RECOMPILE_FAILED.formatted(exceptionDetail(unexpected)), unexpected));
            return;
        }

        val now = statusTimestamp(pdpConfiguration.pdpId());
        getStatusRef(pdpConfiguration.pdpId()).set(new PdpStatus(PdpState.LOADED, pdpConfiguration.configurationId(),
                pdpConfiguration.combiningAlgorithm(), pdpConfiguration.saplDocuments().size(), now, null, null));
        publishVoter(pdpConfiguration.pdpId(), newConfiguration);
    }

    /** Call under {@link #stateLock}. */
    private void recordUnexpectedFailure(PDPConfiguration configuration, OnCompileError onCompileError,
            PluginsBundle plugins, ServedState previous, Throwable failure) {
        val pdpId    = configuration.pdpId();
        val reason   = ERROR_CONFIGURATION_REJECTED.formatted(configuration.configurationId(), pdpId,
                ERROR_UNEXPECTED_CONFIGURATION_FAILURE.formatted(exceptionDetail(failure)));
        val lastGood = previous.configuration().orElse(null);
        if (failure instanceof RuntimeException && onCompileError == OnCompileError.RETAIN_LAST_GOOD
                && hasLastGood(previous.status().state()) && lastGood != null && lastGood.plugins() == plugins) {
            val voterChanged = getCurrentConfiguration(pdpId).orElse(null) != lastGood;
            getConfigRef(pdpId).set(previous.configuration());
            val status = previous.status();
            getStatusRef(pdpId).set(new PdpStatus(PdpState.STALE, status.configurationId(), status.combiningAlgorithm(),
                    status.documentCount(), status.lastSuccessfulLoad(), null, reason));
            if (previous.generation() == null) {
                servedGeneration.remove(pdpId);
            } else {
                servedGeneration.put(pdpId, previous.generation());
            }
            if (voterChanged) {
                notifyListeners(pdpId, new PdpUpdateEvent.Voter(pdpId, lastGood));
            }
        } else {
            getStatusRef(pdpId).set(
                    new PdpStatus(PdpState.ERROR, null, null, 0, previous.status().lastSuccessfulLoad(), null, reason));
            val error = new SaplCompilerException(reason, failure);
            publishVoter(pdpId, PdpCompiler.createErrorVoter(configuration, error, plugins));
        }
        log.warn("{}", reason, failure);
    }

    private void handleCompileFailure(PDPConfiguration pdpConfiguration, OnCompileError onCompileError,
            PluginsBundle plugins, SaplCompilerException compilerException) {
        val formattedError = ERROR_CONFIGURATION_REJECTED.formatted(pdpConfiguration.configurationId(),
                pdpConfiguration.pdpId(), CompilationErrorFormatter.format(compilerException));
        if (compilerException.getCause() == null) {
            log.warn("{}", formattedError);
        } else {
            log.warn("{}", formattedError, compilerException);
        }
        val pdpId = pdpConfiguration.pdpId();
        val now   = statusTimestamp(pdpId);
        // Keep serving the last-good voter (STALE) only on a config update, where that
        // voter is still bound to the live runtime. A plugin swap (FAIL_CLOSED) must
        // fail closed instead: the last-good voter is bound to the retired bundle.
        if (onCompileError == OnCompileError.RETAIN_LAST_GOOD && hasLastGood(getStatusRef(pdpId).get().state())
                && getCurrentConfiguration(pdpId).filter(voter -> voter.plugins() == plugins).isPresent()) {
            markStale(pdpId, now, formattedError);
        } else {
            val errorVoter = PdpCompiler.createErrorVoter(pdpConfiguration, compilerException, plugins);
            markError(pdpId, now, formattedError);
            publishVoter(pdpId, errorVoter);
        }
    }

    private void publishVoter(String pdpId, CompiledPdp voter) {
        getConfigRef(pdpId).set(Optional.of(voter));
        servedGeneration.put(pdpId, pluginGeneration);
        notifyListeners(pdpId, new PdpUpdateEvent.Voter(pdpId, voter));
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
        case ConfigurationEvent.NewConfiguration(var configuration)         -> loadConfiguration(configuration);
        case ConfigurationEvent.ConfigurationRemoved(var pdpId)             -> removeConfigurationForPdp(pdpId);
        case ConfigurationEvent.ConfigurationError(var pdpId, var reason)   -> reportConfigurationError(pdpId, reason);
        case ConfigurationEvent.ConfigurationExpired(var pdpId, var reason) -> expireConfigurationForPdp(pdpId, reason);
        }
    }

    /**
     * Records that a source could not produce a usable configuration for a pdpId.
     * A last-good voter keeps serving and the PDP transitions to STALE; when there
     * is none, the PDP transitions to ERROR and no voter is served, so decisions
     * resolve to the fail-closed no-configuration vote. The served voter is never
     * replaced, since a {@code ConfigurationError} carries no configuration to
     * compile.
     */
    private void reportConfigurationError(String pdpId, String reason) {
        updates.execute(() -> "record a configuration error for pdpId '%s'".formatted(pdpId),
                () -> reportConfigurationErrorNow(pdpId, reason));
    }

    /** Call under {@link #stateLock}. */
    private void reportConfigurationErrorNow(String pdpId, String reason) {
        if (!ignoreWhenClosed(() -> "record a configuration error for pdpId '%s'".formatted(pdpId))) {
            reportConfigurationErrorLocked(pdpId, reason);
        }
    }

    /** Keeps the last-good voter (STALE) or fails closed (ERROR) with a reason. Call under {@link #stateLock}. */
    private void reportConfigurationErrorLocked(String pdpId, String reason) {
        val now = statusTimestamp(pdpId);
        if (hasLastGood(getStatusRef(pdpId).get().state())) {
            markStale(pdpId, now, reason);
        } else {
            markError(pdpId, now, reason);
        }
        log.warn(WARN_CONFIGURATION_REJECTED, reason);
    }

    // The extension processor is host code. Its calls are isolated so a throw never
    // crashes the configuration path, a throwing prepare fails closed as a rejection.
    private PreparationResult prepareExtensions(String pdpId, PDPConfiguration configuration) {
        try {
            val result = extensionsProcessor.prepare(pdpId, configuration);
            if (result == null) {
                return new Rejected(ERROR_EXTENSIONS_PREPARATION_RESULT_MISSING.formatted(pdpId));
            }
            return result;
        } catch (RuntimeException | AssertionError e) {
            val exceptionMessage = e.getMessage();
            val message          = exceptionMessage == null || exceptionMessage.isBlank()
                    ? ERROR_EXCEPTION_MESSAGE_MISSING
                    : exceptionMessage;
            val reason           = ERROR_EXTENSIONS_PREPARATION_FAILED.formatted(e.getClass().getSimpleName(), message);
            val formattedError   = ERROR_CONFIGURATION_REJECTED.formatted(configuration.configurationId(), pdpId,
                    reason);
            log.warn("{}", formattedError, e);
            return new Rejected(reason);
        }
    }

    private void commitExtensions(String pdpId, PDPConfiguration configuration) {
        try {
            extensionsProcessor.commit(pdpId, configuration);
        } catch (RuntimeException | AssertionError e) {
            log.warn(WARN_EXTENSION_COMMIT_FAILED, configuration.configurationId(), pdpId, exceptionDetail(e), e);
        }
    }

    private void removeExtensions(String pdpId) {
        try {
            extensionsProcessor.remove(pdpId);
        } catch (RuntimeException | AssertionError e) {
            log.warn(WARN_EXTENSION_REMOVAL_FAILED, pdpId, exceptionDetail(e), e);
        }
    }

    /**
     * Fails a pdpId closed while keeping it visible. The served voter is dropped so
     * decisions resolve to the fail-closed no-configuration vote, the retained
     * configuration is discarded so a later plugins recompile cannot resurrect the
     * expired configuration, and the PDP transitions to ERROR. A later
     * {@code NewConfiguration} repopulates and re-serves normally.
     */
    private void expireConfigurationForPdp(String pdpId, String reason) {
        updates.execute(() -> "expire the configuration for pdpId '%s'".formatted(pdpId),
                () -> expireConfigurationNow(pdpId, reason));
    }

    /** Call under {@link #stateLock}. */
    private void expireConfigurationNow(String pdpId, String reason) {
        if (ignoreWhenClosed(() -> "expire the configuration for pdpId '%s'".formatted(pdpId))) {
            return;
        }
        val now = statusTimestamp(pdpId);
        retainedConfigurations.remove(pdpId);
        servedGeneration.remove(pdpId);
        getConfigRef(pdpId).set(Optional.empty());
        markError(pdpId, now, reason);
        notifyListeners(pdpId, new PdpUpdateEvent.Removed(pdpId));
        removeExtensions(pdpId);
        log.warn(WARN_CONFIGURATION_EXPIRED, reason);
    }

    private static boolean hasLastGood(PdpState state) {
        return state == PdpState.LOADED || state == PdpState.STALE;
    }

    /** Keeps serving the last-good voter and records the failure reason. Call under {@link #stateLock}. */
    private void markStale(String pdpId, @Nullable Instant now, String reason) {
        getStatusRef(pdpId).updateAndGet(current -> new PdpStatus(PdpState.STALE, current.configurationId(),
                current.combiningAlgorithm(), current.documentCount(), current.lastSuccessfulLoad(), now, reason));
    }

    /** No servable configuration: fail closed and record the failure reason. Call under {@link #stateLock}. */
    private void markError(String pdpId, @Nullable Instant now, String reason) {
        getStatusRef(pdpId).updateAndGet(
                current -> new PdpStatus(PdpState.ERROR, null, null, 0, current.lastSuccessfulLoad(), now, reason));
    }

    private @Nullable Instant statusTimestamp(String pdpId) {
        try {
            return clock.instant();
        } catch (DateTimeException e) {
            log.warn(WARN_STATUS_TIMESTAMP_UNAVAILABLE, pdpId, e);
            return null;
        }
    }

    /**
     * Removes the configuration for a PDP.
     * A removal requested by a callback runs after its current transition.
     *
     * @param pdpId the PDP identifier
     */
    public void removeConfigurationForPdp(String pdpId) {
        updates.execute(() -> "remove the configuration for pdpId '%s'".formatted(pdpId),
                () -> removeConfigurationNow(pdpId));
    }

    /** Call under {@link #stateLock}. */
    private void removeConfigurationNow(String pdpId) {
        if (ignoreWhenClosed(() -> "remove the configuration for pdpId '%s'".formatted(pdpId))) {
            return;
        }
        retainedConfigurations.remove(pdpId);
        servedGeneration.remove(pdpId);
        getConfigRef(pdpId).set(Optional.empty());
        statusCache.remove(pdpId);
        configCache.remove(pdpId);
        notifyListeners(pdpId, new PdpUpdateEvent.Removed(pdpId));
        removeExtensions(pdpId);
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
     * Changes requested by the initial callback are queued until it returns.
     *
     * @param pdpId the PDP identifier
     * @param listener the callback to invoke on configuration change
     */
    public void subscribeToUpdates(String pdpId, Consumer<PdpUpdateEvent> listener) {
        if (updates.isProcessingOnCurrentThread()) {
            subscribeNow(pdpId, listener);
        } else {
            updates.execute(() -> "subscribe to updates for pdpId '%s'".formatted(pdpId),
                    () -> subscribeNow(pdpId, listener));
        }
    }

    /** Call under {@link #stateLock}. */
    private void subscribeNow(String pdpId, Consumer<PdpUpdateEvent> listener) {
        if (ignoreWhenClosed(() -> "subscribe to updates for pdpId '%s'".formatted(pdpId))) {
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
    }

    /**
     * Removes a previously registered listener.
     * Unsubscribing after this source closes is a silent, idempotent cleanup.
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
     * A close requested by a callback runs after its current transition.
     */
    @Override
    public void close() {
        updates.execute(() -> "close the configuration source", this::closeNow);
    }

    /** Call under {@link #stateLock}. */
    private void closeNow() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            try {
                pluginsSource.unsubscribe(pluginListener);
            } catch (RuntimeException | AssertionError failure) {
                log.warn(WARN_PLUGIN_UNSUBSCRIBE_FAILED, exceptionDetail(failure), failure);
            }
            for (val pdpId : retainedConfigurations.keySet()) {
                removeExtensions(pdpId);
            }
        } finally {
            updateListeners.clear();
            configCache.clear();
            statusCache.clear();
            retainedConfigurations.clear();
            servedGeneration.clear();
        }
    }

    private void notifyListeners(String pdpId, PdpUpdateEvent event) {
        val set = updateListeners.get(pdpId);
        if (set == null) {
            return;
        }
        for (val listener : copyOf(set)) {
            notifyListener(listener, pdpId, event);
        }
    }

    private void notifyListener(Consumer<PdpUpdateEvent> listener, String pdpId, PdpUpdateEvent event) {
        try {
            listener.accept(event);
        } catch (Exception | AssertionError e) {
            val operation = switch (event) {
            case PdpUpdateEvent.Voter(var ignoredId, var voter) ->
                "configuration '%s'".formatted(voter.metadata().configurationId());
            case PdpUpdateEvent.Removed(var ignoredId)          -> "configuration removal or absence";
            };
            log.warn(WARN_LISTENER_FAILED, pdpId, operation, exceptionDetail(e), e);
        }
    }

    /** Call under {@link #stateLock}. */
    private boolean ignoreWhenClosed(Supplier<String> operation) {
        if (!closed) {
            return false;
        }
        log.warn(WARN_CONFIGURATION_SOURCE_CLOSED, operation.get());
        return true;
    }

    private static String exceptionDetail(Throwable failure) {
        val message = failure.getMessage();
        return failure.getClass().getSimpleName() + ": "
                + (message == null || message.isBlank() ? ERROR_EXCEPTION_MESSAGE_MISSING : message);
    }

    private AtomicReference<Optional<CompiledPdp>> getConfigRef(String pdpId) {
        return configCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(Optional.empty()));
    }

    private AtomicReference<PdpStatus> getStatusRef(String pdpId) {
        return statusCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(PdpStatus.initial()));
    }

}
