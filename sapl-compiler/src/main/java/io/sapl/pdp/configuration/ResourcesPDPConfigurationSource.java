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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.sapl.api.pdp.PDPConfiguration;

/**
 * PDP configuration source that loads configurations from classpath resources.
 * This is useful for bundling policies
 * with an application JAR.
 * <p>
 * The source supports both single-PDP and multi-PDP setups:
 * <p>
 * <b>Single PDP (root-level files):</b>
 *
 * <pre>
 * /policies/
 * ├── pdp.json
 * ├── access.sapl
 * └── audit.sapl
 * </pre>
 *
 * Result: pdpId = "default"
 * <p>
 * <b>Multi-PDP (subdirectories):</b>
 *
 * <pre>
 * /policies/
 * ├── production/
 * │   ├── pdp.json
 * │   └── strict.sapl
 * └── development/
 *     ├── pdp.json
 *     └── permissive.sapl
 * </pre>
 *
 * Result: The callback is invoked once for each subdirectory configuration.
 * <p>
 * <b>Mixed (backward compatible):</b>
 *
 * <pre>
 * /policies/
 * ├── pdp.json          (default PDP)
 * ├── main.sapl         (default PDP)
 * └── tenant-a/
 *     ├── pdp.json
 *     └── tenant.sapl
 * </pre>
 *
 * Result: The callback is invoked for both "default" and "tenant-a"
 * configurations.
 * <p>
 * Since resources are static, this source invokes the callback once per
 * configuration during construction. There is no
 * hot-reloading from classpath resources.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. All configurations are loaded during construction
 * and the callback is invoked
 * synchronously. After construction, the source holds no mutable state.
 * </p>
 * <h2>Security Measures</h2>
 * <ul>
 * <li><b>PDP identifier validation:</b> All PDP identifiers are validated
 * against a strict pattern (alphanumeric,
 * hyphens, underscores, dots) with a maximum length of 255 characters.</li>
 * <li><b>Classpath isolation:</b> Resources are loaded exclusively from the
 * configured classpath prefix using
 * ClassGraph, preventing access to arbitrary filesystem locations.</li>
 * <li><b>Sanitized error messages:</b> Exceptions do not expose internal
 * classpath details or system paths.</li>
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
 * public ResourcesPDPConfigurationSource resourcesSource() {
 *     return new ResourcesPDPConfigurationSource("/policies",
 *             config -> configRegister.loadConfiguration(config, false));
 * }
 * }</pre>
 */
@Slf4j
public final class ResourcesPDPConfigurationSource implements PDPConfigurationSource {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final String DEFAULT_RESOURCE_PATH = "/policies";

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading from the default resource path "/policies".
     *
     * @param callback
     * called for each configuration found in the resources
     */
    public ResourcesPDPConfigurationSource(@NonNull Consumer<PDPConfiguration> callback) {
        this(DEFAULT_RESOURCE_PATH, callback);
    }

    /**
     * Creates a source loading from a custom resource path.
     * <p>
     * The configuration ID is determined from pdp.json if present, otherwise
     * auto-generated in the format:
     * {@code res:<path>@sha256:<hash>}
     * </p>
     *
     * @param resourcePath
     * the classpath resource path (e.g., "/policies" or "/custom/config")
     * @param callback
     * called for each configuration found in the resources
     */
    public ResourcesPDPConfigurationSource(@NonNull String resourcePath, @NonNull Consumer<PDPConfiguration> callback) {
        log.info("Loading PDP configurations from classpath resources: '{}'.", resourcePath);
        loadConfigurations(resourcePath, callback);
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            log.debug("Disposed resource configuration source.");
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    private void loadConfigurations(String resourcePath, Consumer<PDPConfiguration> callback) {
        val normalizedPath = normalizeResourcePath(resourcePath);
        val scannedData    = scanResources(normalizedPath);

        var configurationsLoaded = loadRootConfiguration(normalizedPath, scannedData, callback);
        configurationsLoaded += loadSubdirectoryConfigurations(normalizedPath, scannedData, callback);

        log.info("Loaded {} PDP configurations from resources.", configurationsLoaded);
    }

    private ScannedResourceData scanResources(String normalizedPath) {
        val    rootSaplFiles    = new HashMap<String, String>();
        val    subDirectoryData = new HashMap<String, Map<String, String>>();
        String rootPdpJson      = null;

        try (var scanResult = new ClassGraph().acceptPaths(normalizedPath).scan()) {
            for (var resource : scanResult.getAllResources()) {
                val relativePath = getRelativePath(resource.getPath(), normalizedPath);

                if (relativePath.contains("/")) {
                    processSubdirectoryResource(relativePath, resource, subDirectoryData);
                } else {
                    if (PDP_JSON.equals(relativePath)) {
                        rootPdpJson = readResource(resource);
                    } else if (relativePath.endsWith(SAPL_EXTENSION)) {
                        rootSaplFiles.put(relativePath, readResource(resource));
                    }
                }
            }
        }

        return new ScannedResourceData(rootPdpJson, rootSaplFiles, subDirectoryData);
    }

    private void processSubdirectoryResource(String relativePath, Resource resource,
            Map<String, Map<String, String>> subDirectoryData) {
        val slashIndex   = relativePath.indexOf('/');
        val subdirName   = relativePath.substring(0, slashIndex);
        val fileInSubdir = relativePath.substring(slashIndex + 1);

        subDirectoryData.computeIfAbsent(subdirName, k -> new HashMap<>());

        if (PDP_JSON.equals(fileInSubdir) || fileInSubdir.endsWith(SAPL_EXTENSION)) {
            subDirectoryData.get(subdirName).put(fileInSubdir, readResource(resource));
        }
    }

    private int loadRootConfiguration(String normalizedPath, ScannedResourceData data,
            Consumer<PDPConfiguration> callback) {
        if (data.rootPdpJson() == null && data.rootSaplFiles().isEmpty()) {
            return 0;
        }

        val sourcePath    = "/" + normalizedPath;
        val defaultConfig = PDPConfigurationLoader.loadFromContent(data.rootPdpJson(), data.rootSaplFiles(),
                DEFAULT_PDP_ID, sourcePath);
        callback.accept(defaultConfig);
        log.debug("Loaded default PDP configuration with {} SAPL documents.", data.rootSaplFiles().size());
        return 1;
    }

    private int loadSubdirectoryConfigurations(String normalizedPath, ScannedResourceData data,
            Consumer<PDPConfiguration> callback) {
        var count = 0;

        for (val entry : data.subDirectoryData().entrySet()) {
            val subdirName = entry.getKey();

            if (!PDPConfigurationSource.isValidPdpId(subdirName)) {
                log.warn("Skipping subdirectory with invalid name: {}.", subdirName);
                continue;
            }

            val subdirData = entry.getValue();
            val pdpJson    = subdirData.remove(PDP_JSON);
            val saplFiles  = extractSaplFiles(subdirData);

            if (pdpJson != null || !saplFiles.isEmpty()) {
                val sourcePath = "/" + normalizedPath + "/" + subdirName;
                val config     = PDPConfigurationLoader.loadFromContent(pdpJson, saplFiles, subdirName, sourcePath);
                callback.accept(config);
                count++;
                log.debug("Loaded PDP configuration '{}' with {} SAPL documents.", subdirName, saplFiles.size());
            }
        }

        return count;
    }

    private record ScannedResourceData(
            String rootPdpJson,
            Map<String, String> rootSaplFiles,
            Map<String, Map<String, String>> subDirectoryData) {}

    private Map<String, String> extractSaplFiles(Map<String, String> content) {
        val saplFiles = new HashMap<String, String>();
        for (val entry : content.entrySet()) {
            if (entry.getKey().endsWith(SAPL_EXTENSION)) {
                saplFiles.put(entry.getKey(), entry.getValue());
            }
        }
        return saplFiles;
    }

    private String normalizeResourcePath(String path) {
        var normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getRelativePath(String fullPath, String basePath) {
        if (fullPath.startsWith(basePath + "/")) {
            return fullPath.substring(basePath.length() + 1);
        }
        if (fullPath.startsWith(basePath)) {
            return fullPath.substring(basePath.length());
        }
        return fullPath;
    }

    private String readResource(Resource resource) {
        try {
            return new String(resource.load(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to read resource: %s.".formatted(resource.getPath()), e);
        }
    }

}
