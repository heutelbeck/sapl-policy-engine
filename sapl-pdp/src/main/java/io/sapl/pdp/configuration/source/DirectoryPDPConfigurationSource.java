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
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import reactor.core.Disposable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PDP configuration source that loads configurations from a filesystem
 * directory with file watching for hot-reload.
 * <p>
 * The source monitors a directory for .sapl policy files and an optional
 * pdp.json configuration file. When files
 * change, the configuration is reloaded into the voter source.
 * </p>
 * <h2>Directory Layout</h2>
 *
 * <pre>
 * /policies/
 * |-- pdp.json        (optional - combining algorithm and configurationId)
 * |-- access.sapl
 * \-- audit.sapl
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
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The file monitoring thread runs independently and
 * loads configurations directly into the voter source.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>PDP ID validation:</b> Validated against strict pattern.</li>
 * </ul>
 */
@Slf4j
public final class DirectoryPDPConfigurationSource implements Disposable {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private static final String ERROR_FAILED_TO_LOAD_INITIAL_CONFIGURATION = "Failed to load initial configuration for PDP '{}': {}. "
            + "File monitoring will continue and configuration will be loaded when a valid configuration is provided.";
    private static final String ERROR_FAILED_TO_START_FILE_MONITOR         = "Failed to start file monitor for configuration directory: '%s'.";
    private static final String ERROR_PATH_IS_NOT_DIRECTORY                = "Configuration path is not a directory: '%s'.";

    private final Path                  directoryPath;
    private final String                pdpId;
    private final PdpVoterSource        pdpVoterSource;
    private final Runnable              onDirectoryRemoved;
    private final FileAlterationMonitor monitor;
    private final AtomicBoolean         disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading from the specified directory with default PDP ID.
     *
     * @param directoryPath
     * the filesystem directory containing policy files
     * @param pdpVoterSource
     * the voter source to load configurations into
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath, @NonNull PdpVoterSource pdpVoterSource) {
        this(directoryPath, PdpIdValidator.DEFAULT_PDP_ID, pdpVoterSource);
    }

    /**
     * Creates a source loading from the specified directory with a custom PDP ID.
     *
     * @param directoryPath
     * the filesystem directory containing policy files
     * @param pdpId
     * the PDP identifier for loaded configurations
     * @param pdpVoterSource
     * the voter source to load configurations into
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull String pdpId,
            @NonNull PdpVoterSource pdpVoterSource) {
        this(directoryPath, pdpId, pdpVoterSource, () -> {});
    }

    DirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull String pdpId,
            @NonNull PdpVoterSource pdpVoterSource,
            @NonNull Runnable onDirectoryRemoved) {
        PdpIdValidator.validatePdpId(pdpId);
        this.directoryPath      = PdpIdValidator.resolveHomeFolderIfPresent(directoryPath).toAbsolutePath().normalize();
        this.pdpId              = pdpId;
        this.pdpVoterSource     = pdpVoterSource;
        this.onDirectoryRemoved = onDirectoryRemoved;
        this.monitor            = new FileAlterationMonitor(POLL_INTERVAL_MS);

        log.info("Loading PDP configuration '{}' from directory: '{}'.", pdpId, this.directoryPath);
        validateDirectory();
        try {
            loadAndNotify();
        } catch (Exception e) {
            // Intentionally not rethrowing: the file monitor started below will
            // detect corrected files and automatically reload a valid configuration.
            log.error(ERROR_FAILED_TO_LOAD_INITIAL_CONFIGURATION, pdpId, e.getMessage(), e);
        }
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
            throw new PDPConfigurationException(ERROR_PATH_IS_NOT_DIRECTORY.formatted(directoryPath));
        }
    }

    private void loadAndNotify() {
        val config = PDPConfigurationLoader.loadFromDirectory(directoryPath, pdpId);
        pdpVoterSource.loadConfiguration(config, true);
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
            if (disposed.get() || Files.exists(directoryPath)) {
                return;
            }
            if (disposed.compareAndSet(false, true)) {
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
