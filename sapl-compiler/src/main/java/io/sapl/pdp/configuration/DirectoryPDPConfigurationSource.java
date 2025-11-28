/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * PDP configuration source that loads configurations from a filesystem
 * directory with file watching for hot-reload.
 * <p>
 * The source monitors a directory for .sapl policy files and an optional
 * pdp.json configuration file. When files
 * change, the callback is invoked with the updated configuration.
 * <p>
 * Directory layout:
 *
 * <pre>
 * /policies/
 * ├── pdp.json        (optional - combining algorithm config)
 * ├── access.sapl
 * └── audit.sapl
 * </pre>
 * <p>
 * The callback is invoked:
 * <ul>
 * <li>Once during construction with the initial configuration</li>
 * <li>On each file change (create, modify, delete) after debouncing</li>
 * </ul>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The file monitoring thread runs independently and
 * invokes the callback directly. The
 * callback implementation must handle thread safety for its own state.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>Symlink protection:</b> Symbolic links are rejected.</li>
 * <li><b>Resource limits:</b> Total size limited to 10 MB, max 1000 files.</li>
 * <li><b>PDP ID validation:</b> Validated against strict pattern.</li>
 * </ul>
 *
 * @see ConfigurationUpdateCallback
 */
@Slf4j
public class DirectoryPDPConfigurationSource implements PDPConfigurationSource {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private static final long MAX_TOTAL_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int  MAX_FILE_COUNT       = 1000;

    private final Path                        directoryPath;
    private final String                      pdpId;
    private final String                      configurationId;
    private final ConfigurationUpdateCallback callback;
    private final FileAlterationMonitor       monitor;
    private final AtomicBoolean               disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading from the specified directory with default PDP ID.
     *
     * @param directoryPath
     * the filesystem directory containing policy files
     * @param callback
     * called when configuration is loaded or updated
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath, @NonNull ConfigurationUpdateCallback callback) {
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
            @NonNull ConfigurationUpdateCallback callback) {
        this(directoryPath, pdpId, "directory-config", callback);
    }

    /**
     * Creates a source with full configuration options.
     *
     * @param directoryPath
     * the filesystem directory containing policy files
     * @param pdpId
     * the PDP identifier for loaded configurations
     * @param configurationId
     * the configuration version identifier
     * @param callback
     * called when configuration is loaded or updated
     */
    public DirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull String pdpId,
            @NonNull String configurationId,
            @NonNull ConfigurationUpdateCallback callback) {
        PDPConfigurationSource.validatePdpId(pdpId);
        this.directoryPath   = directoryPath.toAbsolutePath().normalize();
        this.pdpId           = pdpId;
        this.configurationId = configurationId;
        this.callback        = callback;
        this.monitor         = new FileAlterationMonitor(POLL_INTERVAL_MS);

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
        if (!Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new PDPConfigurationException("Configuration directory does not exist.");
        }
        if (Files.isSymbolicLink(directoryPath)) {
            throw new PDPConfigurationException("Configuration directory must not be a symbolic link.");
        }
        if (!Files.isDirectory(directoryPath)) {
            throw new PDPConfigurationException("Configuration path is not a directory.");
        }
    }

    private void loadAndNotify() {
        val    saplFiles = new HashMap<String, String>();
        String pdpJson   = null;
        var    totalSize = 0L;
        var    fileCount = 0;

        try {
            val files = listFiles(directoryPath);
            for (val file : files) {
                val fileName = file.getFileName().toString();

                if (Files.isSymbolicLink(file)) {
                    log.warn("Skipping symbolic link: {}.", fileName);
                    continue;
                }

                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                if (PDP_JSON.equals(fileName)) {
                    totalSize = validateAndTrackSize(file, totalSize);
                    pdpJson   = readFile(file);
                    fileCount++;
                } else if (fileName.endsWith(SAPL_EXTENSION)) {
                    totalSize = validateAndTrackSize(file, totalSize);
                    saplFiles.put(fileName, readFile(file));
                    fileCount++;
                }

                validateFileCount(fileCount);
            }

            val config = PDPConfigurationLoader.loadFromContent(pdpJson, saplFiles, pdpId, configurationId);
            callback.onConfigurationUpdate(config);
            log.debug("Loaded PDP configuration '{}' with {} SAPL documents.", pdpId, saplFiles.size());

        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to load configuration from directory.", e);
        }
    }

    private long validateAndTrackSize(Path file, long currentTotal) throws IOException {
        val fileSize = Files.size(file);
        val newTotal = currentTotal + fileSize;
        if (newTotal > MAX_TOTAL_SIZE_BYTES) {
            throw new PDPConfigurationException(
                    "Total configuration size exceeds maximum of %d MB.".formatted(MAX_TOTAL_SIZE_BYTES / 1024 / 1024));
        }
        return newTotal;
    }

    private void validateFileCount(int count) {
        if (count > MAX_FILE_COUNT) {
            throw new PDPConfigurationException("File count exceeds maximum of %d files.".formatted(MAX_FILE_COUNT));
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
            throw new PDPConfigurationException("Failed to start file monitor for configuration directory.", e);
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

    private List<Path> listFiles(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.toList();
        }
    }

    private String readFile(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
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
