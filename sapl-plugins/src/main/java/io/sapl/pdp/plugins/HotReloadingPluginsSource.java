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
import io.sapl.attributes.broker.pip.PipHandle;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * {@link PluginsSource} backed by the {@link SaplPluginManager} that
 * hot-reloads plugin contributions when the plugins
 * directory changes.
 * <p>
 * Two kinds of contribution reload through two different mechanisms, matching
 * how the engine treats each:
 * <ul>
 * <li><b>Function libraries</b> &mdash; a fresh {@link FunctionBroker} is built
 * (host base libraries plus the current
 * plugin libraries) and emitted inside a new {@link PluginsBundle}. The
 * {@code PdpVoterSource} recompiles every
 * retained configuration against the new broker.</li>
 * <li><b>Policy Information Points</b> &mdash; reconciled directly against the
 * live
 * {@link PolicyInformationPointAttributeBroker}: the previously loaded plugin
 * PIPs are unloaded and the current set is
 * loaded, since the broker is fixed for the lifetime of the PDP but supports
 * runtime mutation.</li>
 * </ul>
 * A {@link FileAlterationMonitor} polls the plugins directory for {@code *.jar}
 * changes. All create, modify, and delete events of a single poll cycle are
 * coalesced into one {@link SaplPluginManager#reload()} followed by a single
 * republish, so a batch of JAR changes triggers exactly one recompile.
 *
 * @since 4.1.0
 */
@Slf4j
public final class HotReloadingPluginsSource implements PluginsSource {

    private static final String JAR_SUFFIX              = ".jar";
    private static final long   POLL_INTERVAL_MS        = 500;
    private static final long   MONITOR_STOP_TIMEOUT_MS = 5000;

    private final SaplPluginManager                     manager;
    private final PolicyInformationPointAttributeBroker attributeBroker;
    private final int                                   functionCacheSize;
    private final boolean                               includeDefaultFunctionLibraries;
    private final List<DecisionInterceptor>             decisionInterceptors;
    private final List<SubscriptionLifecycleListener>   lifecycleListeners;

    private final ReentrantLock                  stateLock  = new ReentrantLock();
    private final Set<Consumer<PluginsBundle>>   listeners  = ConcurrentHashMap.newKeySet();
    private final AtomicReference<PluginsBundle> bundle     = new AtomicReference<>();
    private final List<PipHandle>                loadedPips = new ArrayList<>();
    private final AtomicBoolean                  closed     = new AtomicBoolean(false);

    private final FileAlterationMonitor monitor;

    /**
     * Builds the source, starts the plugin manager, publishes the initial bundle,
     * registers the initial plugin PIPs,
     * and begins watching the plugins directory.
     *
     * @param manager
     * the plugin manager providing the contributions
     * @param attributeBroker
     * the live broker plugin PIPs are loaded into
     * @param functionCacheSize
     * function result cache size for the rebuilt broker
     * @param includeDefaultFunctionLibraries
     * whether to include the SAPL default function libraries in the rebuilt broker
     * @param decisionInterceptors
     * decision interceptors carried in every bundle
     * @param lifecycleListeners
     * lifecycle listeners carried in every bundle
     */
    public HotReloadingPluginsSource(@NonNull SaplPluginManager manager,
            @NonNull PolicyInformationPointAttributeBroker attributeBroker,
            int functionCacheSize,
            boolean includeDefaultFunctionLibraries,
            @NonNull List<DecisionInterceptor> decisionInterceptors,
            @NonNull List<SubscriptionLifecycleListener> lifecycleListeners) {
        this.manager                         = manager;
        this.attributeBroker                 = attributeBroker;
        this.functionCacheSize               = functionCacheSize;
        this.includeDefaultFunctionLibraries = includeDefaultFunctionLibraries;
        this.decisionInterceptors            = List.copyOf(decisionInterceptors);
        this.lifecycleListeners              = List.copyOf(lifecycleListeners);

        manager.start();
        republish();

        this.monitor = startDirectoryMonitor(manager.getProperties().getPluginsPath());
    }

    /**
     * Rebuilds the {@link FunctionBroker}, reconciles plugin PIPs against the live
     * attribute broker, and notifies
     * subscribers with the new bundle.
     */
    public void republish() {
        stateLock.lock();
        try {
            if (closed.get()) {
                return;
            }
            reconcilePips();

            val libraries      = manager.functionLibraries();
            val functionBroker = PolicyDecisionPointBuilder.buildFunctionBroker(functionCacheSize,
                    includeDefaultFunctionLibraries, libraries);

            val interceptors = new ArrayList<>(decisionInterceptors);
            interceptors.addAll(manager.decisionInterceptors());

            val bundle = new PluginsBundle(functionBroker, interceptors, lifecycleListeners);
            this.bundle.set(bundle);
            for (val listener : listeners) {
                notify(listener, bundle);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void reconcilePips() {
        for (val handle : loadedPips) {
            handle.unload();
        }
        loadedPips.clear();
        for (val pip : manager.policyInformationPoints()) {
            loadedPips.add(attributeBroker.load(pip));
        }
    }

    private FileAlterationMonitor startDirectoryMonitor(Path pluginsRoot) {
        try {
            val observer = FileAlterationObserver.builder().setFile(pluginsRoot.toFile())
                    .setFileFilter(this::isPluginJar).get();
            observer.addListener(new PluginDirectoryListener());

            val fileMonitor = new FileAlterationMonitor(POLL_INTERVAL_MS);
            fileMonitor.setThreadFactory(Thread.ofVirtual().name("sapl-plugin-watcher").factory());
            fileMonitor.addObserver(observer);
            fileMonitor.start();
            log.info("Enabling hot-reloading SAPL plugins from directory: {}", pluginsRoot);
            return fileMonitor;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to watch plugins directory: " + pluginsRoot, e);
        }
    }

    private boolean isPluginJar(File file) {
        return !file.isDirectory() && file.getName().endsWith(JAR_SUFFIX);
    }

    private void stopMonitorSafely() {
        try {
            monitor.stop(MONITOR_STOP_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("Error stopping plugin directory monitor: {}", e.getMessage());
        }
    }

    @Override
    public void subscribe(@NonNull Consumer<PluginsBundle> listener) {
        if (closed.get()) {
            return;
        }
        if (listeners.add(listener)) {
            val snapshot = bundle.get();
            if (snapshot != null) {
                notify(listener, snapshot);
            }
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<PluginsBundle> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        closed.set(true);

        stopMonitorSafely();
        stateLock.lock();
        try {
            listeners.clear();
            for (val handle : loadedPips) {
                handle.unload();
            }
            loadedPips.clear();
        } finally {
            stateLock.unlock();
        }
        manager.close();
    }

    private void notify(Consumer<PluginsBundle> listener, PluginsBundle bundle) {
        try {
            listener.accept(bundle);
        } catch (Exception e) {
            log.warn("Plugins bundle listener threw: {}", e.getMessage());
        }
    }

    /**
     * Coalesces all file events of a single poll cycle into one reload. The
     * per-file callbacks only raise a flag; the actual
     * {@link SaplPluginManager#reload()} and republish run once per cycle in
     * {@link #onStop(FileAlterationObserver)}. The monitor drives all callbacks
     * of an observer from a single thread, so the flag needs no synchronization.
     */
    private final class PluginDirectoryListener extends FileAlterationListenerAdaptor {

        private boolean dirty;

        @Override
        public void onStart(FileAlterationObserver observer) {
            dirty = false;
        }

        @Override
        public void onFileCreate(File file) {
            dirty = true;
        }

        @Override
        public void onFileChange(File file) {
            dirty = true;
        }

        @Override
        public void onFileDelete(File file) {
            dirty = true;
        }

        @Override
        public void onStop(FileAlterationObserver observer) {
            if (!dirty || closed.get()) {
                return;
            }
            try {
                manager.reload();
                republish();
            } catch (Exception e) {
                log.warn("Plugin reload after directory change failed: {}", e.getMessage());
            }
        }
    }

}
