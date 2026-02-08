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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PDP configuration source that loads multiple PDP configurations from
 * subdirectories of a root directory.
 * <p>
 * Each subdirectory represents a separate PDP configuration, with the
 * subdirectory name used as the PDP ID. Optionally,
 * root-level files can be loaded as a "default" PDP for backward compatibility.
 * </p>
 * <h2>Directory Layout</h2>
 *
 * <pre>
 * /policies/
 * ├── pdp.json              (optional - default PDP, if includeRootFiles=true)
 * ├── main-policy.sapl      (optional - default PDP, if includeRootFiles=true)
 * ├── production/
 * │   ├── pdp.json
 * │   └── strict-access.sapl
 * ├── staging/
 * │   ├── pdp.json
 * │   └── relaxed-access.sapl
 * └── development/
 *     └── permissive.sapl
 * </pre>
 * <p>
 * This source delegates to {@link DirectoryPDPConfigurationSource} for each
 * subdirectory, providing full hot-reload
 * support. When subdirectories are added or removed, corresponding sources are
 * created or disposed.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. Child sources are managed in a ConcurrentHashMap,
 * and directory monitoring runs on a
 * background thread.
 * </p>
 *
 * @see DirectoryPDPConfigurationSource
 */
@Slf4j
public final class MultiDirectoryPDPConfigurationSource implements Disposable {

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private static final String ERROR_FAILED_TO_LOAD_CONFIGURATIONS = "Failed to load configurations from directory.";
    private static final String ERROR_FAILED_TO_START_MONITOR       = "Failed to start directory monitor.";
    private static final String ERROR_PATH_IS_NOT_DIRECTORY         = "Configuration path is not a directory.";

    private final Path                                         directoryPath;
    private final boolean                                      includeRootFiles;
    private final PdpVoterSource                               pdpVoterSource;
    private final FileAlterationMonitor                        monitor;
    private final Map<String, DirectoryPDPConfigurationSource> childSources = new ConcurrentHashMap<>();
    private final AtomicBoolean                                disposed     = new AtomicBoolean(false);

    /**
     * Creates a source loading from subdirectories, excluding root-level files.
     *
     * @param directoryPath
     * the root directory containing PDP subdirectories
     * @param pdpVoterSource
     * the voter source to load configurations into
     */
    public MultiDirectoryPDPConfigurationSource(@NonNull Path directoryPath, @NonNull PdpVoterSource pdpVoterSource) {
        this(directoryPath, false, pdpVoterSource);
    }

    /**
     * Creates a source with control over root-level file handling.
     *
     * @param directoryPath
     * the root directory containing PDP subdirectories
     * @param includeRootFiles
     * if true, root-level .sapl and pdp.json files are loaded as
     * "default" PDP
     * @param pdpVoterSource
     * the voter source to load configurations into
     */
    public MultiDirectoryPDPConfigurationSource(@NonNull Path directoryPath,
            boolean includeRootFiles,
            @NonNull PdpVoterSource pdpVoterSource) {
        this.directoryPath    = PdpIdValidator.resolveHomeFolderIfPresent(directoryPath).toAbsolutePath().normalize();
        this.includeRootFiles = includeRootFiles;
        this.pdpVoterSource   = pdpVoterSource;
        this.monitor          = new FileAlterationMonitor(POLL_INTERVAL_MS);

        log.info("Loading PDP configurations from multi-directory source: '{}'.", this.directoryPath);
        validateDirectory();
        loadInitialSources();
        startDirectoryMonitor();
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            stopMonitorSafely();
            disposeAllChildSources();
            log.debug("Disposed multi-directory configuration source.");
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

    private void loadInitialSources() {
        try (var stream = Files.list(directoryPath)) {
            val entries = stream.toList();

            for (val entry : entries) {
                if (Files.isDirectory(entry)) {
                    createChildSource(entry);
                }
            }

            if (includeRootFiles) {
                createRootSource();
            }

            log.info("Loaded {} PDP configurations from subdirectories.", childSources.size());
        } catch (Exception e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_LOAD_CONFIGURATIONS, e);
        }
    }

    private void createChildSource(Path subdirectory) {
        val fileNamePath = subdirectory.getFileName();
        if (fileNamePath == null) {
            log.warn("Skipping subdirectory with no name: {}.", subdirectory);
            return;
        }
        val pdpId = fileNamePath.toString();

        if (!PdpIdValidator.isValidPdpId(pdpId)) {
            log.warn("Skipping subdirectory with invalid name: {}.", pdpId);
            return;
        }

        if (childSources.containsKey(pdpId)) {
            log.warn("Duplicate PDP ID '{}', skipping.", pdpId);
            return;
        }

        try {
            val source = new DirectoryPDPConfigurationSource(subdirectory, pdpId, pdpVoterSource);
            childSources.put(pdpId, source);
            log.debug("Created child source for PDP '{}'.", pdpId);
        } catch (Exception e) {
            log.error("Failed to create child source for PDP '{}': {}.", pdpId, e.getMessage(), e);
        }
    }

    private void createRootSource() {
        if (childSources.containsKey(PdpIdValidator.DEFAULT_PDP_ID)) {
            log.warn("Subdirectory named 'default' exists, root files will not be loaded as default PDP.");
            return;
        }

        try {
            val source = new DirectoryPDPConfigurationSource(directoryPath, PdpIdValidator.DEFAULT_PDP_ID,
                    pdpVoterSource);
            childSources.put(PdpIdValidator.DEFAULT_PDP_ID, source);
            log.debug("Created root source for default PDP.");
        } catch (Exception e) {
            log.error("Failed to create root source for default PDP: {}.", e.getMessage(), e);
        }
    }

    private void startDirectoryMonitor() {
        try {
            val observer = FileAlterationObserver.builder().setFile(directoryPath.toFile())
                    .setFileFilter(File::isDirectory).get();

            observer.addListener(new SubdirectoryChangeListener());
            monitor.addObserver(observer);
            monitor.start();
            log.debug("Started directory monitoring on: {}.", directoryPath);
        } catch (Exception e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_START_MONITOR, e);
        }
    }

    private void stopMonitorSafely() {
        try {
            monitor.stop(MONITOR_STOP_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("Error stopping directory monitor.", e);
            try {
                monitor.stop(0);
            } catch (Exception e2) {
                log.error("Failed to force stop directory monitor.", e2);
            }
        }
    }

    private void disposeAllChildSources() {
        for (val entry : childSources.entrySet()) {
            try {
                entry.getValue().dispose();
            } catch (Exception e) {
                log.warn("Error disposing child source for PDP '{}': {}.", entry.getKey(), e.getMessage());
            }
        }
        childSources.clear();
    }

    /**
     * Listener for subdirectory additions and removals.
     */
    private class SubdirectoryChangeListener extends FileAlterationListenerAdaptor {

        @Override
        public void onDirectoryCreate(File directory) {
            if (disposed.get()) {
                return;
            }
            val path = directory.toPath();
            log.debug("Detected new subdirectory: {}.", directory.getName());
            createChildSource(path);
        }

        @Override
        public void onDirectoryDelete(File directory) {
            if (disposed.get()) {
                return;
            }
            val pdpId = directory.getName();
            log.debug("Detected subdirectory removal: {}.", pdpId);
            removeChildSource(pdpId);
        }

        private void removeChildSource(String pdpId) {
            val source = childSources.remove(pdpId);
            if (source != null) {
                source.dispose();
                pdpVoterSource.removeConfigurationForPdp(pdpId);
                log.debug("Removed and disposed child source for PDP '{}'.", pdpId);
            }
        }
    }

}
