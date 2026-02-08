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

import io.sapl.api.pdp.PDPConfiguration;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * PDP configuration source that loads configurations from a filesystem
 * directory with file watching for hot-reload.
 * <p>
 * The source monitors a directory for .sapl policy files and an optional
 * pdp.json configuration file. When files
 * change, the callback is invoked with the updated configuration.
 * </p>
 * <h2>Directory Layout</h2>
 *
 * <pre>
 * /policies/
 * ├── pdp.json        (optional - combining algorithm and configurationId)
 * ├── access.sapl
 * └── audit.sapl
 * </pre>
 *
 * <h2>Configuration ID</h2>
 * <p>
 * The configuration ID is used for audit and correlation of authorization
 * decisions with the exact policy set. If
 * pdp.json contains a {@code configurationId} field, that value is used.
 * Otherwise, an ID is auto-generated in the
 * format: {@code dir:<path>@<timestamp>@sha256:<hash>}
 * </p>
 * <h2>Callback Invocation</h2>
 * <p>
 * The callback is invoked:
 * </p>
 * <ul>
 * <li>Once during construction with the initial configuration</li>
 * <li>On each file change (create, modify, delete)</li>
 * </ul>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The file monitoring thread runs independently and
 * invokes the callback directly. The
 * callback implementation must handle thread safety for its own state.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>PDP ID validation:</b> Validated against strict pattern.</li>
 * </ul>
 * <h2>Spring Integration</h2>
 * <p>
 * This class implements {@link reactor.core.Disposable} but Spring does not
 * automatically call {@code dispose()} on
 * Reactor's Disposable interface. When exposing as a Spring bean, explicitly
 * specify the destroy method:
 * </p>
 *
 * <pre>{@code
 * @Bean(destroyMethod = "dispose")
 * public DirectoryPDPConfigurationSource directorySource() {
 *     return new DirectoryPDPConfigurationSource(Path.of("/policies"),
 *             security -> configRegister.loadConfiguration(security, false));
 * }
 * }</pre>
 */
@Slf4j
public final class DirectoryPDPConfigurationSource implements PDPConfigurationSource {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private static final String ERROR_FAILED_TO_START_FILE_MONITOR = "Failed to start file monitor for configuration directory.";
    private static final String ERROR_PATH_IS_NOT_DIRECTORY        = "Configuration path is not a directory.";

    private final Path                       directoryPath;
    private final String                     pdpId;
    private final Consumer<PDPConfiguration> callback;
    private final FileAlterationMonitor      monitor;
    private final AtomicBoolean              disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading from the specified directory with default PDP ID.
     *
     * @param directoryPath
     * the filesystem directory containing policy files
     * @param callback
     * called when configuration is loaded or updated
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath, @NonNull Consumer<PDPConfiguration> callback) {
        this(directoryPath, DEFAULT_PDP_ID, callback);
    }

    /**
     * Creates a source loading from the specified directory with a custom PDP ID.
     *
     * @param directoryPath
     * the filesystem directory containing policy files
     * @param pdpId
     * the PDP identifier for loaded configurations
     * @param callback
     * called when configuration is loaded or updated
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull String pdpId,
            @NonNull Consumer<PDPConfiguration> callback) {
        PDPConfigurationSource.validatePdpId(pdpId);
        this.directoryPath = PDPConfigurationSource.resolveHomeFolderIfPresent(directoryPath).toAbsolutePath()
                .normalize();
        this.pdpId         = pdpId;
        this.callback      = callback;
        this.monitor       = new FileAlterationMonitor(POLL_INTERVAL_MS);

        log.info("Loading PDP configuration '{}' from directory: '{}'.", pdpId, this.directoryPath);
        validateDirectory();
        loadAndNotify();
        startFileMonitor();
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            stopMonitorSafely();
            log.debug("Disposed directory configuration source for PDP '{}'.", pdpId);
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    private void validateDirectory() {
        if (!Files.isDirectory(directoryPath)) {
            throw new PDPConfigurationException(ERROR_PATH_IS_NOT_DIRECTORY);
        }
    }

    private void loadAndNotify() {
        val config = PDPConfigurationLoader.loadFromDirectory(directoryPath, pdpId);
        callback.accept(config);
        log.debug("Loaded PDP configuration '{}' with {} SAPL documents.", pdpId, config.saplDocuments().size());
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
            throw new PDPConfigurationException(ERROR_FAILED_TO_START_FILE_MONITOR, e);
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
            if (disposed.get()) {
                return;
            }
            log.debug("Detected file change: {}.", file.getName());
            try {
                loadAndNotify();
                log.info("Reloaded PDP configuration '{}'.", pdpId);
            } catch (Exception e) {
                log.error("Failed to reload configuration for PDP '{}': {}.", pdpId, e.getMessage(), e);
            }
        }
    }

}
