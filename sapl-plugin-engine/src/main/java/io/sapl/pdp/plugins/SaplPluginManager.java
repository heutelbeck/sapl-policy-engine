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

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.plugins.SaplDecisionInterceptorPlugin;
import io.sapl.api.pdp.plugins.SaplFunctionLibraryPlugin;
import io.sapl.api.pdp.plugins.SaplPolicyInformationPointPlugin;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Loads SAPL plugins from a directory of JAR files using PF4J and exposes their
 * contributions as the function libraries
 * and Policy Information Points they declare through
 * {@link SaplFunctionLibraryPlugin} and
 * {@link SaplPolicyInformationPointPlugin} extensions.
 * <p>
 * The manager exposes the plugin contributions through accessor methods
 * ({@link #functionLibraries()}, {@link #policyInformationPoints()}, and
 * {@link #decisionInterceptors()}) so a host application (for example the
 * Spring Boot auto-configuration) can pick them up at startup with no extra
 * wiring. For runtime hot-reloading, drive a
 * {@link HotReloadingPluginsSource} from this manager.
 * <p>
 * All public methods are guarded by a single {@link ReentrantLock}, in line
 * with the engine's thread-safety convention.
 *
 * @since 4.1.0
 */
@Slf4j
@Getter
public class SaplPluginManager implements AutoCloseable {

    private final Path          pluginsPath;
    private final PluginManager pluginManager;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean             closed;

    /**
     * Creates a manager over the configured plugins directory, backed by a
     * {@link JarPluginManager}. Pins the PF4J system version to
     * {@link SaplVersion#SEMANTIC_VERSION} so that a plugin declaring an
     * incompatible {@code Plugin-Requires} range is rejected at load time.
     *
     * @param pluginsPath
     * the directory scanned for plugin JARs
     */
    public SaplPluginManager(Path pluginsPath) {
        this(new JarPluginManager(pluginsPath), pluginsPath);
        pluginManager.setSystemVersion(SaplVersion.SEMANTIC_VERSION);
    }

    /**
     * Creates a manager around a pre-configured PF4J {@link PluginManager}.
     * Mainly intended for tests and advanced customization. Unlike
     * {@link #SaplPluginManager(Path)} this does not pin the
     * system version; the caller configures the supplied manager.
     *
     * @param pluginManager
     * the PF4J plugin manager to delegate to
     * @param pluginsPath
     * the directory scanned for plugin JARs
     */
    public SaplPluginManager(PluginManager pluginManager, Path pluginsPath) {
        this.pluginManager = pluginManager;
        this.pluginsPath   = pluginsPath;
    }

    /**
     * Loads and starts all plugins found in the plugins directory.
     */
    public void start() {
        lock.lock();
        try {
            ensureOpen();
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
            log.info("Started {} SAPL plugin(s).", pluginManager.getStartedPlugins().size());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops, unloads, and re-loads every plugin so that JARs added to or removed
     * from the plugins directory take
     * effect. Safe to call while the manager is running.
     */
    public void reload() {
        lock.lock();
        try {
            ensureOpen();
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
            log.info("Reloaded SAPL plugins; {} plugin(s) now active.", pluginManager.getStartedPlugins().size());
        } finally {
            lock.unlock();
        }
    }

    public List<Object> functionLibraries() {
        lock.lock();
        try {
            ensureOpen();
            val libraries = new ArrayList<>();
            for (val extension : pluginManager.getExtensions(SaplFunctionLibraryPlugin.class)) {
                libraries.addAll(extension.functionLibraries());
            }
            return List.copyOf(libraries);
        } finally {
            lock.unlock();
        }
    }

    public List<Object> policyInformationPoints() {
        lock.lock();
        try {
            ensureOpen();
            val pips = new ArrayList<>();
            for (val extension : pluginManager.getExtensions(SaplPolicyInformationPointPlugin.class)) {
                pips.addAll(extension.policyInformationPoints());
            }
            return List.copyOf(pips);
        } finally {
            lock.unlock();
        }
    }

    public List<DecisionInterceptor> decisionInterceptors() {
        lock.lock();
        try {
            ensureOpen();
            val interceptors = new ArrayList<DecisionInterceptor>();
            for (val extension : pluginManager.getExtensions(SaplDecisionInterceptorPlugin.class)) {
                interceptors.addAll(extension.decisionInterceptors());
            }
            return List.copyOf(interceptors);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return an immutable snapshot of the currently loaded plugins
     */
    public List<PluginWrapper> plugins() {
        lock.lock();
        try {
            return List.copyOf(pluginManager.getPlugins());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops and unloads all plugins. Idempotent.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
        } finally {
            lock.unlock();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SaplPluginManager has been closed.");
        }
    }

}
