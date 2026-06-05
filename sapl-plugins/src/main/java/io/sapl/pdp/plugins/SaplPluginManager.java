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
import io.sapl.api.attributes.PolicyInformationPointProvider;
import io.sapl.api.functions.FunctionLibraryProvider;
import io.sapl.api.pdp.plugins.SaplFunctionLibraryPlugin;
import io.sapl.api.pdp.plugins.SaplPolicyInformationPointPlugin;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

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
 * The manager implements both {@link FunctionLibraryProvider} and
 * {@link PolicyInformationPointProvider} so a host
 * application (for example the Spring Boot auto-configuration) discovers the
 * plugin contributions at startup with no
 * extra wiring. For runtime hot-reloading, drive a
 * {@link HotReloadingPluginsSource} from this manager.
 * <p>
 * All public methods are guarded by a single {@link ReentrantLock}, in line
 * with the engine's thread-safety convention.
 *
 * @since 4.1.0
 */
@Slf4j
public class SaplPluginManager implements FunctionLibraryProvider, PolicyInformationPointProvider, AutoCloseable {

    private final SaplPluginsProperties properties;
    private final PluginManager         pluginManager;
    private final ReentrantLock         lock = new ReentrantLock();
    private boolean                     closed;

    /**
     * Creates a manager over the configured plugins directory, backed by a
     * {@link JarPluginManager}. Pins the PF4J system version to
     * {@link SaplVersion#SEMANTIC_VERSION} so that a plugin declaring an
     * incompatible {@code Plugin-Requires} range is rejected at load time.
     *
     * @param properties
     * the plugin configuration, including the directory scanned for plugin JARs
     */
    public SaplPluginManager(SaplPluginsProperties properties) {
        this(new JarPluginManager(properties.getPluginsPath()), properties);
        pluginManager.setSystemVersion(SaplVersion.SEMANTIC_VERSION);
    }

    /**
     * Creates a manager around a pre-configured PF4J {@link PluginManager}.
     * Mainly intended for tests and advanced customization. Unlike
     * {@link #SaplPluginManager(SaplPluginsProperties)} this does not pin the
     * system version; the caller configures the supplied manager.
     *
     * @param pluginManager
     * the PF4J plugin manager to delegate to
     * @param properties
     * the plugin configuration
     */
    public SaplPluginManager(PluginManager pluginManager, SaplPluginsProperties properties) {
        this.pluginManager = pluginManager;
        this.properties    = properties;
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

    @Override
    public List<Object> functionLibraries() {
        lock.lock();
        try {
            val libraries = new ArrayList<>();
            for (val extension : pluginManager.getExtensions(SaplFunctionLibraryPlugin.class)) {
                libraries.addAll(extension.functionLibraries());
            }
            return List.copyOf(libraries);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Object> policyInformationPoints() {
        lock.lock();
        try {
            val pips = new ArrayList<>();
            for (val extension : pluginManager.getExtensions(SaplPolicyInformationPointPlugin.class)) {
                pips.addAll(extension.policyInformationPoints());
            }
            return List.copyOf(pips);
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
