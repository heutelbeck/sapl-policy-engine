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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
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
 * <li><b>Function libraries</b> &mdash; a new {@link PluginsSnapshot} is
 * emitted. The {@code PdpVoterSource} recompiles every retained configuration
 * against it; each configuration's {@link FunctionBroker} is built from the
 * base libraries plus the libraries of the plugins that configuration
 * requests.</li>
 * <li><b>Policy Information Points</b> &mdash; reconciled directly against the
 * live
 * {@link PolicyInformationPointAttributeBroker}: the previously loaded plugin
 * PIPs are unloaded and the current set is
 * loaded, since the broker is fixed for the lifetime of the PDP but supports
 * runtime mutation. Plugin PIPs are loaded with their plugin id, so they are
 * visible only to PDPs that enable the plugin, and PIPs of different plugins
 * may provide the same attribute. A plugin whose PIPs cannot be loaded, for
 * example because an attribute collides with a base PIP or with another PIP of
 * the same plugin, is excluded from the snapshot with all of its contributions;
 * the other plugins remain available.</li>
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
    private final List<Object>                          baseFunctionLibraries;
    private final List<DecisionInterceptor>             decisionInterceptors;
    private final List<SubscriptionLifecycleListener>   lifecycleListeners;

    private final ReentrantLock                    stateLock  = new ReentrantLock();
    private final Set<Consumer<PluginsSnapshot>>   listeners  = ConcurrentHashMap.newKeySet();
    private final AtomicReference<PluginsSnapshot> snapshot   = new AtomicReference<>();
    private final List<PipHandle>                  loadedPips = new ArrayList<>();
    private final AtomicBoolean                    closed     = new AtomicBoolean(false);

    private final FileAlterationMonitor monitor;

    /**
     * Builds the source, starts the plugin manager, publishes the initial snapshot,
     * registers the initial plugin PIPs,
     * and begins watching the plugins directory.
     *
     * @param manager the plugin manager providing the contributions
     * @param baseFunctionLibraries function libraries that are not from plugins, available to every
     * configuration
     * @param attributeBroker the live broker plugin PIPs are loaded into
     * @param functionCacheSize function result cache size for the function brokers built per
     * configuration
     * @param includeDefaultFunctionLibraries whether to include the SAPL default function libraries in the base
     * @param decisionInterceptors base decision interceptors, carried in every bundle
     * @param lifecycleListeners base lifecycle listeners, carried in every bundle
     */
    public HotReloadingPluginsSource(@NonNull SaplPluginManager manager,
            List<Object> baseFunctionLibraries,
            @NonNull PolicyInformationPointAttributeBroker attributeBroker,
            int functionCacheSize,
            boolean includeDefaultFunctionLibraries,
            @NonNull List<DecisionInterceptor> decisionInterceptors,
            @NonNull List<SubscriptionLifecycleListener> lifecycleListeners) {
        this.manager                         = manager;
        this.baseFunctionLibraries           = baseFunctionLibraries;
        this.attributeBroker                 = attributeBroker;
        this.functionCacheSize               = functionCacheSize;
        this.includeDefaultFunctionLibraries = includeDefaultFunctionLibraries;
        this.decisionInterceptors            = List.copyOf(decisionInterceptors);
        this.lifecycleListeners              = List.copyOf(lifecycleListeners);

        manager.start();
        republish();

        try {
            this.monitor = startDirectoryMonitor(manager.getPluginsPath());
        } catch (RuntimeException e) {
            manager.close();
            throw e;
        }
    }

    /**
     * Reconciles plugin PIPs against the live attribute broker, builds a new
     * snapshot of the available plugins, and notifies subscribers with it.
     */
    public void republish() {
        stateLock.lock();
        try {
            if (closed.get()) {
                return;
            }
            val available = reconcilePips(manager.loadedPlugins());
            val snapshot  = new PluginEngineSnapshot(available, baseFunctionLibraries, includeDefaultFunctionLibraries,
                    functionCacheSize, decisionInterceptors, lifecycleListeners);
            this.snapshot.set(snapshot);
            for (val listener : listeners) {
                notify(listener, snapshot);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private Map<String, LoadedPlugin> reconcilePips(Map<String, LoadedPlugin> loadedPlugins) {
        for (val handle : loadedPips) {
            handle.unload();
        }
        loadedPips.clear();
        val available = new HashMap<String, LoadedPlugin>();
        for (val plugin : loadedPlugins.values()) {
            val handles = new ArrayList<PipHandle>();
            try {
                for (val pip : plugin.policyInformationPoints()) {
                    handles.add(attributeBroker.load(pip, plugin.pluginId()));
                }
                loadedPips.addAll(handles);
                available.put(plugin.pluginId(), plugin);
            } catch (RuntimeException e) {
                handles.forEach(PipHandle::unload);
                log.error("PIPs of plugin '{}' could not be loaded. Plugin is not available.", plugin.pluginId(), e);
            }
        }

        return available;
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
    public void subscribe(@NonNull Consumer<PluginsSnapshot> listener) {
        if (closed.get()) {
            return;
        }
        if (listeners.add(listener)) {
            val snapshot = this.snapshot.get();
            if (snapshot != null) {
                notify(listener, snapshot);
            }
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<PluginsSnapshot> listener) {
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

    private void notify(Consumer<PluginsSnapshot> listener, PluginsSnapshot snapshot) {
        try {
            listener.accept(snapshot);
        } catch (Exception e) {
            log.warn("Plugins snapshot listener threw: {}", e.getMessage());
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
                log.error("Plugin reload after directory change failed.", e);
            }
        }
    }

}
