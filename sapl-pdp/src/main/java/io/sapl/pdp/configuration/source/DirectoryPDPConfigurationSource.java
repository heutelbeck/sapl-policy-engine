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

import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * PDP configuration source that loads configurations from a filesystem
 * directory with file watching for hot-reload.
 * <p>
 * The source monitors a directory for .sapl policy files and a required
 * pdp.json configuration file. The first {@link #subscribe(Consumer)}
 * loads the initial configuration and starts file monitoring. Subsequent
 * file changes emit a fresh {@link ConfigurationEvent.Load} to all
 * subscribers.
 * </p>
 * <h2>Directory Layout</h2>
 *
 * <pre>
 * /policies/
 *   pdp.json        (required - combining algorithm and configurationId)
 *   access.sapl
 *   audit.sapl
 * </pre>
 *
 * <h2>Configuration ID</h2>
 * <p>
 * The configuration ID is used for audit and correlation of authorization
 * decisions with the exact policy set. If
 * pdp.json contains a {@code configurationId} field, that value is used.
 * Otherwise, an ID is auto-generated in the
 * format: {@code dir:<path>@<timestamp>}
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The file monitoring thread runs independently and
 * emits events to subscribed listeners.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>PDP ID validation:</b> Validated against strict pattern.</li>
 * </ul>
 */
@Slf4j
public final class DirectoryPDPConfigurationSource implements PDPConfigurationSource {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private static final String ERROR_DIRECTORY_MISSING                    = "Policy directory for PDP '{}' is missing: '{}'. Every decision is INDETERMINATE until the directory exists with a valid configuration. Decisions resume automatically once it is created.";
    private static final String ERROR_FAILED_TO_LOAD_INITIAL_CONFIGURATION = "The configuration for PDP '{}' could not be loaded: {}. Every decision is INDETERMINATE until a valid configuration is in place. Decisions resume automatically once it is corrected.";
    private static final String ERROR_FAILED_TO_START_FILE_MONITOR         = "Failed to start file monitor for configuration directory: '%s'.";
    private static final String ERROR_PATH_IS_NOT_DIRECTORY                = "Configuration path is not a directory: '%s'.";
    private static final String WARN_SUBSCRIBER_THREW                      = "Configuration subscriber for PDP '{}' threw and was isolated; other subscribers and hot-reload are unaffected: {}";

    private final Path                              directoryPath;
    private final String                            pdpId;
    private final Runnable                          onDirectoryRemoved;
    private final boolean                           recoverOnDirectoryRemoval;
    private final FileAlterationMonitor             monitor;
    private final Set<Consumer<ConfigurationEvent>> subscribers      = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                     activated        = new AtomicBoolean(false);
    private final AtomicBoolean                     closed           = new AtomicBoolean(false);
    private final AtomicBoolean                     directoryPresent = new AtomicBoolean(true);

    /**
     * Creates a source for the specified directory with default PDP ID.
     *
     * @param directoryPath the filesystem directory containing policy files
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath) {
        this(directoryPath, PdpIdValidator.DEFAULT_PDP_ID);
    }

    /**
     * Creates a source for the specified directory with a custom PDP ID.
     *
     * @param directoryPath the filesystem directory containing policy files
     * @param pdpId the PDP identifier for loaded configurations
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath, @NonNull String pdpId) {
        // Standalone: on removal, emit a Remove and keep watching to recover when the
        // directory reappears.
        this(directoryPath, pdpId, () -> {}, true);
    }

    DirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull String pdpId,
            @NonNull Runnable onDirectoryRemoved) {
        // Parent-managed (MultiDirectory child): on removal, self-close and delegate
        // recovery to the parent.
        this(directoryPath, pdpId, onDirectoryRemoved, false);
    }

    private DirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull String pdpId,
            @NonNull Runnable onDirectoryRemoved,
            boolean recoverOnDirectoryRemoval) {
        PdpIdValidator.validatePdpId(pdpId);
        this.directoryPath             = PdpIdValidator.resolveHomeFolderIfPresent(directoryPath).toAbsolutePath()
                .normalize();
        this.pdpId                     = pdpId;
        this.onDirectoryRemoved        = onDirectoryRemoved;
        this.recoverOnDirectoryRemoval = recoverOnDirectoryRemoval;
        this.monitor                   = new FileAlterationMonitor(POLL_INTERVAL_MS);
    }

    @Override
    public void subscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        if (closed.get()) {
            return;
        }
        subscribers.add(listener);
        if (activated.compareAndSet(false, true)) {
            activate();
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        subscribers.remove(listener);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stopMonitorSafely();
            subscribers.clear();
            log.debug("Closed directory configuration source for PDP '{}'.", pdpId);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private void activate() {
        log.info("Loading PDP configuration '{}' from directory: '{}'.", pdpId, directoryPath);
        if (!Files.isDirectory(directoryPath)) {
            // A non-directory path, or a parent-managed child whose directory is gone, is
            // an unrecoverable
            // misconfiguration, so fail fast. A standalone source whose directory does not
            // exist yet is
            // transiently absent, so log and keep watching.
            if (Files.exists(directoryPath) || !recoverOnDirectoryRemoval) {
                throw new PDPConfigurationException(ERROR_PATH_IS_NOT_DIRECTORY.formatted(directoryPath));
            }
            directoryPresent.set(false);
            log.error(ERROR_DIRECTORY_MISSING, pdpId, directoryPath);
            startFileMonitor();
            return;
        }
        try {
            loadAndEmit();
        } catch (Exception e) {
            // Intentionally not rethrowing: the file monitor started below will
            // detect corrected files and automatically reload a valid configuration.
            log.error(ERROR_FAILED_TO_LOAD_INITIAL_CONFIGURATION, pdpId, e.getMessage(), e);
        }
        startFileMonitor();
    }

    private void loadAndEmit() {
        val config = PDPConfigurationLoader.loadFromDirectory(directoryPath, pdpId);
        emit(new ConfigurationEvent.Load(config, true));
        log.debug("Loaded PDP configuration '{}' with {} SAPL documents.", pdpId, config.saplDocuments().size());
    }

    private void emit(ConfigurationEvent event) {
        if (closed.get()) {
            return;
        }
        for (val subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                // Isolate subscribers: a throwing one must not skip the others or
                // escape onto the file-monitor thread.
                log.warn(WARN_SUBSCRIBER_THREW, pdpId, e.getMessage());
            }
        }
    }

    private void startFileMonitor() {
        try {
            val observer = FileAlterationObserver.builder().setFile(directoryPath.toFile())
                    .setFileFilter(this::isRelevantFile).get();

            observer.addListener(new DirectoryChangeListener());
            monitor.addObserver(observer);
            monitor.start();
            log.debug("Started file monitoring on directory: {}.", directoryPath);
        } catch (Exception e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_START_FILE_MONITOR.formatted(directoryPath), e);
        }
    }

    private boolean isRelevantFile(File file) {
        if (file.isDirectory()) {
            return false;
        }
        val name = file.getName();
        return name.endsWith(SAPL_EXTENSION) || PDP_JSON.equals(name);
    }

    private void stopMonitorSafely() {
        try {
            monitor.stop(MONITOR_STOP_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("Error stopping file monitor, attempting interrupt.", e);
            try {
                Thread.currentThread().interrupt();
                monitor.stop(0);
            } catch (Exception e2) {
                log.error("Failed to force stop file monitor.", e2);
            }
        }
    }

    /**
     * Listener for file system changes in the watched directory.
     */
    private class DirectoryChangeListener extends FileAlterationListenerAdaptor {

        @Override
        public void onStart(FileAlterationObserver observer) {
            if (closed.get()) {
                return;
            }
            if (Files.isDirectory(directoryPath)) {
                if (directoryPresent.compareAndSet(false, true)) {
                    log.info("Directory for PDP '{}' reappeared; reloading configuration.", pdpId);
                    handleFileChange(directoryPath.toFile());
                }
                return;
            }
            if (recoverOnDirectoryRemoval) {
                // The directory is gone but may return. Surface its absence as a
                // Remove (so consumers fail closed to INDETERMINATE) and keep
                // watching so a recreation reloads the configuration.
                if (directoryPresent.compareAndSet(true, false)) {
                    log.error(ERROR_DIRECTORY_MISSING, pdpId, directoryPath);
                    emit(new ConfigurationEvent.Remove(pdpId));
                }
                return;
            }
            if (closed.compareAndSet(false, true)) {
                log.info("Directory for PDP '{}' no longer exists, triggering removal.", pdpId);
                Thread.ofPlatform().start(() -> {
                    stopMonitorSafely();
                    onDirectoryRemoved.run();
                });
            }
        }

        @Override
        public void onFileCreate(File file) {
            handleFileChange(file);
        }

        @Override
        public void onFileChange(File file) {
            handleFileChange(file);
        }

        @Override
        public void onFileDelete(File file) {
            handleFileChange(file);
        }

        private void handleFileChange(File file) {
            if (closed.get()) {
                return;
            }
            log.debug("Detected file change: {}.", file.getName());
            try {
                loadAndEmit();
                log.info("Reloaded PDP configuration '{}'.", pdpId);
            } catch (Exception e) {
                log.error("Failed to reload configuration for PDP '{}': {}.", pdpId, e.getMessage(), e);
            }
        }
    }

}
